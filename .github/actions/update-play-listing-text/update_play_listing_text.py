"""Sync the Google Play store listing text from fastlane/metadata/android/<language>.

Every folder under fastlane/metadata/android is a Google Play language (en-US, fr-FR, de-DE,
es-ES, pt-BR). For each one it reads title.txt, short_description.txt and full_description.txt
from the current directory (the repository root), compares them with the live listing and prints
a diff. With APPLY=true it patches only the fields that differ, in one commit; otherwise it
changes nothing. It always checks the Google Play length limits first and deletes its edit on any
failure or dry run.
"""
import difflib
import json
import os
from pathlib import Path

PACKAGE = "com.azimulkabir.actua"
ROOT = Path("fastlane/metadata/android")
# Google Play's limits for each field.
FIELDS = {
    "title": ("title.txt", 30),
    "shortDescription": ("short_description.txt", 80),
    "fullDescription": ("full_description.txt", 4000),
}


def languages():
    found = sorted(path.name for path in ROOT.iterdir() if (path / "title.txt").is_file())
    if "en-US" not in found:
        raise SystemExit(f"{ROOT / 'en-US'} is missing")
    return found


def read_wanted(language):
    folder = ROOT / language
    wanted = {}
    for field, (name, limit) in FIELDS.items():
        text = (folder / name).read_text(encoding="utf-8").strip()
        if not text:
            raise SystemExit(f"{folder / name} is empty")
        if len(text) > limit:
            raise SystemExit(f"{folder / name} has {len(text)} characters; Google Play allows {limit} for {field}")
        wanted[field] = text
    return wanted


def normalized(text):
    """The live text with Windows or old Mac line endings turned into the files' plain newlines."""
    return (text or "").replace("\r\n", "\n").replace("\r", "\n").strip()


def summary(line):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")


def main():
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.errors import HttpError

    wanted = {language: read_wanted(language) for language in languages()}
    key = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "")
    if not key:
        raise SystemExit("PLAY_SERVICE_ACCOUNT_JSON is not configured")
    apply = os.environ.get("APPLY") == "true"
    credentials = service_account.Credentials.from_service_account_info(
        json.loads(key), scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    edits = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False).edits()

    edit_id = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    committed = False
    try:
        updated = []
        for language, fields in wanted.items():
            try:
                live = edits.listings().get(packageName=PACKAGE, editId=edit_id, language=language).execute()
            except HttpError as error:
                if error.resp.status != 404:
                    raise
                live = {}  # a language that has no listing on Google Play yet
            # Compared with normalized line endings, so text that only differs in them is not re-sent every release.
            changed = [field for field in fields if normalized(live.get(field)) != fields[field]]
            if not changed:
                print(f"{language}: listing text already matches the files")
                continue
            for field in changed:
                current = normalized(live.get(field))
                print(f"--- {language} {field}: {len(current)} -> {len(fields[field])} characters")
                for line in difflib.unified_diff(
                    current.splitlines(), fields[field].splitlines(), "live", "files", lineterm="", n=0
                ):
                    print(line)
            if apply:
                # patch, not update: only these fields change, so the listing's video link is kept.
                # A language Google Play has no listing for yet is created from all three fields.
                edits.listings().patch(
                    packageName=PACKAGE, editId=edit_id, language=language,
                    body={field: fields[field] for field in (changed if live else fields)},
                ).execute()
            updated.append(f"{language} ({', '.join(changed)})")
        if not updated:
            print("Google Play listing text already matches the files; nothing to do")
            summary("Google Play listing text already matches the files.")
        elif apply:
            edits.commit(packageName=PACKAGE, editId=edit_id).execute()
            committed = True
            print("Google Play listing text updated: " + "; ".join(updated))
            summary("Google Play listing text updated: " + "; ".join(updated))
        else:
            print("Dry run: nothing was changed. Run the workflow again with apply turned on to publish.")
            summary("Dry run: Google Play listing text differs in " + "; ".join(updated) + "; nothing was changed.")
    except Exception:
        if not committed:
            # Keep the original error: a failure to delete the edit is only reported, never raised over it.
            try:
                edits.delete(packageName=PACKAGE, editId=edit_id).execute()
            except Exception as error:
                print(f"Could not delete Google Play edit {edit_id}: {error}")
        raise
    if not committed:
        edits.delete(packageName=PACKAGE, editId=edit_id).execute()


if __name__ == "__main__":
    main()
