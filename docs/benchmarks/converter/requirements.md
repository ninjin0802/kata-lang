# Unit converter — requirements

The app will be verified on a device by an automated script, so follow these exactly.

- Application id: `dev.kata.converter`. App name: `Converter`.
- **Category**: three elements whose text is exactly `Length`, `Weight`, `Temperature`. Tapping one selects that category. `Length` is selected at start.
- **Units** per category, in this order:
  - Length: `m`, `km`, `cm`, `mi` (1 mi = 1609.344 m)
  - Weight: `kg`, `g`, `lb` (1 lb = 0.45359237 kg)
  - Temperature: `C`, `F`, `K`
- **Two rows of unit choices** for the selected category, each choice an element whose text is exactly the unit symbol. The first row (higher on the screen) selects the source unit, the second row selects the target unit. At start, and whenever the category changes, the source is the category's first unit and the target is its second.
- **Input**: one single-line text field with a numeric keyboard that accepts digits, a decimal point and a leading minus sign. It is empty at start. Changing the category keeps what is typed.
- **Result**: one text element whose text is exactly `= <value> <unit>`, where `<unit>` is the target unit symbol, updated on every change. When the input is empty or is not a number (for example `-`, `.`, `1.2.3`), the text is exactly `= ?` and the app must not crash.
- **Value format**: rounded to at most 6 decimal places, without trailing zeros and without a trailing decimal point (`1.5`, `1000`, `0.621371`, `-40`, `0`). Never `-0`.
- **Swap**: an element whose text is exactly `Swap` exchanges the source and target units.
- **Clear**: an element whose text is exactly `Clear` empties the input.
- Examples: 1500 m → `= 1.5 km`; 1 mi → `= 1.609344 km`; 1 km → `= 0.621371 mi`; 1 kg → `= 1000 g`; 1 lb → `= 0.453592 kg`; 100 C → `= 212 F`; 100 C → `= 373.15 K`; 32 F → `= 0 C`; -40 C → `= -40 F`.
