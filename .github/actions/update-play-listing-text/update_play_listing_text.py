"""Sync the Google Play store listing text from fastlane/metadata/android/en-US.

Reads title.txt, short_description.txt and full_description.txt from the current directory (the
repository root), compares them with the live listing and prints a diff. With APPLY=true it
patches only the fields that differ and commits; otherwise it changes nothing. It always checks
the Google Play length limits first and deletes its edit on any failure or dry run.
"""
import difflib
import json
import os
from pathlib import Path

PACKAGE = "com.azimulkabir.actua"
LANGUAGE = "en-US"
FOLDER = Path("fastlane/metadata/android/en-US")
# Google Play's limits for each field.
FIELDS = {
    "title": ("title.txt", 30),
    "shortDescription": ("short_description.txt", 80),
    "fullDescription": ("full_description.txt", 4000),
}


def read_wanted():
    wanted = {}
    for field, (name, limit) in FIELDS.items():
        text = (FOLDER / name).read_text(encoding="utf-8").strip()
        if not text:
            raise SystemExit(f"{FOLDER / name} is empty")
        if len(text) > limit:
            raise SystemExit(f"{FOLDER / name} has {len(text)} characters; Google Play allows {limit} for {field}")
        wanted[field] = text
    return wanted


def summary(line):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line + "\n")


def main():
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    wanted = read_wanted()
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
        live = edits.listings().get(packageName=PACKAGE, editId=edit_id, language=LANGUAGE).execute()
        changed = [field for field in wanted if (live.get(field) or "").strip() != wanted[field]]
        if not changed:
            print("Google Play listing text already matches the files; nothing to do")
            summary("Google Play listing text already matches the files.")
        for field in changed:
            current = (live.get(field) or "").strip()
            print(f"--- {field}: {len(current)} -> {len(wanted[field])} characters")
            for line in difflib.unified_diff(
                current.splitlines(), wanted[field].splitlines(), "live", "files", lineterm="", n=0
            ):
                print(line)
        if changed and apply:
            # patch, not update: only these fields change, so the listing's video link is kept.
            edits.listings().patch(
                packageName=PACKAGE, editId=edit_id, language=LANGUAGE,
                body={field: wanted[field] for field in changed},
            ).execute()
            edits.commit(packageName=PACKAGE, editId=edit_id).execute()
            committed = True
            print("Google Play listing text updated: " + ", ".join(changed))
            summary("Google Play listing text updated: " + ", ".join(changed))
        elif changed:
            print("Dry run: nothing was changed. Run the workflow again with apply turned on to publish.")
    finally:
        if not committed:
            edits.delete(packageName=PACKAGE, editId=edit_id).execute()


if __name__ == "__main__":
    main()
