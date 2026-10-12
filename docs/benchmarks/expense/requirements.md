# Expense tracker — requirements

The app will be verified on a device by an automated script that reads the screen through the accessibility tree and taps elements by their text, so follow these exactly. "An element with text X" means the text is exactly X (letter case is not compared).

- Application id: `dev.kata.expenses`. App name: `Expenses`.
- An **expense** has an amount (a whole number greater than 0), a note (non-empty text) and a category: `Food`, `Transport`, `Fun` or `Other`.
- All expenses are saved on the device and are still there after the app is closed and started again.

## Navigation

- A bar with three elements with text `List`, `Add` and `Summary`. Tapping one shows that page. The app starts on the List page. The bar is visible on these three pages.

## Add page

- Two single-line input fields: first (higher on the screen) the amount, with a numeric keyboard; second the note, with a normal keyboard. Both are empty at start.
- Four elements with text `Food`, `Transport`, `Fun`, `Other` to choose the category. `Food` is chosen at start.
- An element with text `Save`.
  - If the amount is not a whole number greater than 0: show an element with text `Enter an amount greater than 0` and save nothing.
  - Otherwise, if the note is empty or only spaces: show an element with text `Enter a note` and save nothing.
  - Otherwise: save the expense (note without leading and trailing spaces), empty both fields, choose `Food` again, remove any error text, and show the List page.

## List page

- Five elements with text `All`, `Food`, `Transport`, `Fun`, `Other` to filter by category. `All` is chosen at start.
- An element with text `Sort: newest`. Tapping it changes its text to `Sort: largest`, and tapping again changes it back. With `Sort: newest` the most recently added expense is first (at the top); with `Sort: largest` the largest amount is first.
- An element with text `Total: <sum>`, the sum of the amounts of the expenses currently shown (after filtering), for example `Total: 1500`, or `Total: 0`.
- One row per shown expense, each with: an element whose text is the note; an element with text `<Category> <amount>`, for example `Food 1200`; and an element with text `Delete`.
- When no expense is shown: an element with text `No expenses`.
- Tapping a row's `Delete` shows a confirmation with an element with text `Delete this expense?` and two elements with text `Yes` and `No`. `Yes` deletes that expense; `No` (or dismissing) keeps it. Either way the confirmation goes away.
- Tapping a row's note opens the Detail page for that expense.

## Detail page

- Elements with text `Note: <note>`, `Amount: <amount>` and `Category: <category>`, and an element with text `Back`.
- `Back` returns to the List page. The device's back gesture/button also returns to the List page instead of closing the app.

## Summary page

- For each category that has at least one expense, in the order Food, Transport, Fun, Other: an element with text `<Category>: <sum>`, for example `Food: 1200`. Categories without expenses are not shown.
- An element with text `Count: <number of expenses>`.
- An element with text `Largest: <note> <amount>` for the expense with the largest amount, or `Largest: -` when there are no expenses.
- The Summary ignores the List page's filter.
