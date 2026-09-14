from pathlib import Path

path = Path("app/src/main/java/com/azimulkabir/actua/model/BudgetTarget.kt")
text = path.read_text()
old = '''            var date = runCatching { LocalDate.parse(startDateRaw ?: monthStart.toString()) }.getOrNull() ?: monthStart
            while (date.isAfter(monthStart)) date = date.minusWeeks(1)
            while (date.plusWeeks(1).isBefore(monthStart) || date.plusWeeks(1).isEqual(monthStart)) date = date.plusWeeks(1)
            var weeks = 0L
            while (date.isBefore(nextMonthStart)) {
                if (!date.isBefore(monthStart)) weeks += 1L
                date = date.plusWeeks(1)
            }
'''
new = '''            var date = runCatching { LocalDate.parse(startDateRaw ?: monthStart.toString()) }.getOrNull() ?: monthStart
            while (date.isBefore(monthStart)) date = date.plusWeeks(1)
            var weeks = 0L
            while (date.isBefore(nextMonthStart)) {
                weeks += 1L
                date = date.plusWeeks(1)
            }
'''
if old in text:
    path.write_text(text.replace(old, new, 1))
elif new not in text:
    raise SystemExit("Expected weekly scaling snippet not found")
