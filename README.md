# MyBudget for Android

A native, offline envelope budgeting app. Give every dollar you have a job: put income into envelopes (categories), spend from them, roll what's left into next month, and move money between envelopes when one runs short.

## Download 0.0.5

[Download the signed APK](https://github.com/baslawson/mybudget/releases/download/v0.0.5/MyBudget-0.0.5.apk) · [Release notes](https://github.com/baslawson/mybudget/releases/tag/v0.0.5)

Install it with [Obtainium](https://github.com/ImranR98/Obtainium) to get updates automatically:

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/baslawson/mybudget"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" width="300" alt="Get it on Obtainium"></a>

Android 8+ (API 26). Version `0.0.5`, version code `8`, package `com.mybudget.app`. The universal release APK is approximately 105 KB and uses a dedicated release-signing key. In Obtainium, you can also paste `https://github.com/baslawson/mybudget` as the app source. [Obtainium link documentation](https://wiki.obtainium.imranr.dev/deep_links/).

This release uses local device storage, manual entries, CSV imports, and expenses and upcoming bills from Planner (below). It does not sync with banks; back up to a file or a folder (Settings > Backup). The release signature differs from development/debug builds, so it cannot update those installations in place. Do not uninstall a debug build containing a budget you need to retain: uninstalling deletes that local budget.

## Screenshots

All accounts, payees, amounts and transactions shown below are fictional demo data from a separate preview installation.

<img src="docs/screenshots/home-dark.png" width="220" alt="Home with fictional funding progress in dark theme"> <img src="docs/screenshots/plan-dark.png" width="220" alt="Monthly plan with grouped categories and funding targets">

<img src="docs/screenshots/spending-dark.png" width="220" alt="Fictional spending transactions"> <img src="docs/screenshots/accounts-dark.png" width="220" alt="Fictional cash accounts"> <img src="docs/screenshots/settings-light.png" width="220" alt="Light theme appearance settings">

## Project notes — 7 October 2026

- Native Android app built with Java and Android framework widgets, with no third-party runtime libraries. Android 8+; AUD; local device storage.
- MyBudget branding uses an indigo envelope with a mint checkmark, a matching adaptive launcher icon, and the tagline “Your money. Your plan.” All artwork is vector-based.
- Navigation: Home, Budget, Transactions, Accounts and Reports. The three-dot options menu opens Settings; theme selection lives under Settings > Theme.
- Light, Dark and Auto appearances persist across restarts. Auto follows the device theme, including changes while the app is open. Settings returns to the previous screen.
- Latest UI refinements: compact category rows with Available emphasized; secondary Assigned/Activity values; target descriptions, progress and remaining funding; matching navigation icons; tighter spacing with comfortable touch targets.
- Transaction forms adapt to expense, income and refund. Income hides the category and uses an income-source prompt. Notes are optional. Target editing explains each behavior and shows a deadline only for balance goals.
- Published versions: **0.0.0** (first release, with a signed universal release APK, fictional demo screenshots and an Obtainium installation link), **0.0.1** (expenses from Planner), **0.0.2** (backup and import, envelope planning tools, upcoming and split transactions, credit cards, Planner's upcoming bills), **0.0.3** (fixes: credit-card credits, saving alongside Planner, CSV duplicates, open forms kept, backups), **0.0.4** (fixes: card credit carried over, reports, repeating transactions, card payments, photos) and **0.0.5** (suggestions as you type for groups, payees and notes). The earlier 76.2 KB measurement was for a debug build before the latest UI refinements.
- Verification completed: APK build; calculation checks; Android migration/persistence checks; all five tabs rendered; visual inspection; theme persistence and system-theme switching; income field visibility; conditional target deadline. Checks used the Pixel 7 Pro emulator. Physical-device verification remains outstanding.
- Existing budget data is retained during APK updates (0.0.2 converts it to storage version 4; older MyBudget versions can't read that). New in 0.0.2 (all described below): backup, restore and CSV export; automatic daily backup; CSV import; transaction photos; hide/delete/reorder categories; edit/close/delete accounts; reconcile adjustments; a date picker; payee suggestions; quick assign amounts and Cover overspending; snoozed targets, due days and category notes; Budget reset and Hide amounts; the Reports chart, net worth and money age; upcoming (scheduled and repeating) transactions; split transactions; credit cards; Planner's upcoming bills; and MyBudget's own names for the screens (Budget, Transactions, Reports, To budget). No bank sync is implemented.
- Planner and MyBudget are linked apps. Planner can send paid bills for a confirmed expense and request removal when a payment is undone (from 0.0.1), and its upcoming bills to plan for (from MyBudget 0.0.2 with Planner 0.0.22). BudgetInstrumentation covers adding once and removing on undo, and passed on the Pixel 7 Pro emulator. The full round trip from Planner has not been tested automatically.

## Working agreement

Before future coding, present a plan describing the proposed changes and wait for the user's approval. Implement only after approval. The user likes the current visual direction; preserve the branding and overall style when refining it.

Treat Planner and MyBudget as linked apps in all future plans, code changes and testing. Preserve the payment handoff, duplicate prevention, undo confirmation and stored link identifiers. Changes to the integration contract must account for both apps.

## Planner integration

Planner's “Send paid bills to MyBudget” opens `AddExpenseActivity` using `com.mybudget.app.action.ADD_EXPENSE`. The handoff includes `paymentId`, `billKey`, `payee`, `amountCents` (a long integer), `currency` (`AUD`), `date` (`YYYY-MM-DD`) and an optional `note`. The user confirms the expense before it is saved and chooses its category and account. Repeat bills suggest the category and account from the previous linked entry; an existing payment ID prevents a duplicate expense.

With the same setting on, Planner also sends its **upcoming bills** (unpaid AUD bills from a month back to two months ahead; at most 200) when it opens and when it's left, as an explicit broadcast to `com.mybudget.app` with action `com.mybudget.app.action.UPCOMING_BILLS` and a JSON array extra `bills` of `{id, billKey, payee, due, amountCents?}`. `PlannerBills` checks the list and replaces the last one (kept in preferences, not in the budget, and never entered as money). On Android 14 and later Planner shares its identity with the broadcast and MyBudget accepts only Planner's packages (`io.github.baslawson.planner`, `.debug`). Transactions lists them under Coming up in Planner; each is planned from the category chosen there (`billCategories` in the budget, by `billKey`) or else its last expense's, and then counts in that category's upcoming bills and in Fund targets. The add-expense dialog suggests the same category when the bill is paid, and drops the paid bill from the list at once (by the `upcomingId` extra Planner sends with ADD_EXPENSE; without it, the earliest entry with the same `billKey`). A list more than 7 days old is ignored, with a note in Transactions. Planner adds `FLAG_INCLUDE_STOPPED_PACKAGES`, so the list also reaches a MyBudget that was force-stopped or never opened. Turning the setting off in Planner sends an empty list.

When Planner marks a payment unpaid, `com.mybudget.app.action.PAYMENT_UNDONE` with the same `paymentId` asks whether to remove the linked expense. The user can keep it. Successful handoffs return `RESULT_OK` with a `summary`; cancellation defaults to `RESULT_CANCELED`. Both actions are exported to other apps, so preserve user confirmation before adding or removing data.

Budget storage version 3 persists `externalId` and `billKey` on entries and continues to read version 2 budgets. MainActivity reads the saved data in again when it changed since MainActivity last read or saved it (an expense received from another app), on resume and before each save, so neither side overwrites the other; the add-expense dialog also reads the latest data when it saves. The add-expense dialog leaves out hidden categories and closed accounts (a suggestion pointing at one is dropped). Categories carry optional `hidden`, `snoozed`, `note` and `dueDay` fields and accounts an optional `closed` flag; they read as defaults when missing. Storage version 4 adds `scheduled` (upcoming transactions, which keep a `billKey`) and `splits` on entries whose category is `split`, plus account `type` (`cash` or `credit`) and a payment category's `cardAccount`; MyBudget reads versions 1 to 4 and refuses newer data rather than dropping what it can't read. Backups keep `externalId` and `billKey`, so a payment restored from a backup is still not added twice. Restoring an older backup drops expenses Planner sent after it; if such a bill is later marked unpaid, MyBudget finds nothing to remove and asks nothing. Future changes to storage, transaction editing, activity lifecycle or package/action names must consider this connection and verify the flow between both apps.

## Run

Open this folder in Android Studio, let Gradle sync, and run `app` on an emulator or Android device (Android 8 or newer). Use JDK 17 or 21 for Gradle and install Android SDK 35. Build a debug APK with `gradlew.bat assembleDebug`; the output is `app/build/outputs/apk/debug/app-debug.apk`.

## First budget

Open the **three-dot menu > Settings > Theme** to choose **Light**, **Dark**, or **Auto (follow device)**. The choice is saved across restarts. Auto follows Android's system light/dark setting, including changes while the app is open. Switching appearance preserves the selected screen, month and search; Back from Settings returns to your previous screen.

1. Add cash, checking and savings accounts with their current opening balances.
2. Tap each category to assign some of the money to budget.
3. Record expenses against the category they belong to.
4. Move money from another category when an envelope is overspent.

Amounts are AUD and stored as integer cents. Each month's Budget displays Assigned, Activity and Available for grouped categories. Positive available balances roll forward. Uncovered cash overspending resets the category at rollover and reduces the following month's To budget. Transactions are recorded from an account's opening date onward. Future assignments reserve existing money; future income is never assumed.

Tap categories to assign or return money, move money, edit their group or target, view transactions, move them up or down within their group, hide them, or delete them. A hidden category leaves Budget and the pickers (including Planner's bills) but its money still counts; Budget lists hidden categories at the bottom with what they hold. Deleting a category that was used moves its transactions and monthly assignments to a category you choose, so cash is unchanged and Planner's bills suggest the new category. Assign offers quick amounts: needed for the target, assigned last month, spent last month, average spent over the last three months, and reset to $0 (as far as money not yet spent allows). An overspent category offers Cover overspending: pick a category with money, or To budget, and the amount is filled in. Move Money lists what each category has available. Dates are picked from a calendar and shown as "7 Oct 2026"; they're stored as `YYYY-MM-DD`. The payee field suggests payees used before, and picking one on a new transaction fills in its last category.

A target can be snoozed for the month (it asks for nothing and Fund targets skips it), Refill and Monthly targets can have a due day ("by the 15th"; Fund targets funds the earliest due first), and a category can carry a note shown on its card. The options menu has **Budget reset** (every category's money for the month goes back into To budget, with Undo budget reset on Budget) and **Hide amounts** (dots instead of money until you show them again). Reports adds an income and spending chart for six months (tap a month for its amounts; the list below is the same data), net worth with the change since last month, and Money age: money spent is matched to the oldest money received, and the figure is the average age over the last 10 outflows. Inflows not given to a category (income, reconcile adjustments) are labelled To budget in Transactions and in the CSV.

**Credit cards.** Add account > Credit card, with what you owe now. The card gets a payment category (group "Credit card payments"); what you already owe starts with nothing set aside, so assign money to that category to pay it down. Spending on the card from a category with money moves that money to the payment category, ready for the bill; spending beyond what the category has shows as credit overspending (amber) this month and then becomes card debt, without changing To budget. A refund on the card moves money back. A part into To budget on the card (a reward credit, a reconcile adjustment) isn't cash: it moves money between To budget and the payment category, so what's set aside keeps matching what's owed. Make a payment (on the card in Accounts) is a transfer from a cash account, filled in with what's set aside. Card spending counts as spending in Reports; net worth includes what cards owe; Money age counts the payment, not the card spending. Payment categories can't be spent from directly, deleted, or emptied by Budget reset; deleting an unused card removes its payment category.

**Split transactions.** "Split into categories" in the transaction form spreads one expense or refund over two or more categories (a part can also go into To budget, e.g. cash back). The amount becomes the parts' total; each part counts in its own category, Transactions lists the categories, and the CSV has one row per part. Splits can't be upcoming yet.

**Upcoming transactions.** A new transaction with a future date, or with a repeat (weekly, every 2 weeks, monthly, every 3 months, yearly), becomes upcoming. It isn't money yet: Transactions lists it under Upcoming, Home says when some are due, and on its day you enter it, skip it or edit it; nothing is entered without your tap. A repeating one dated today or earlier is entered at once and its repeats continue; monthly repeats keep their day (31st, then the 28th in February, then the 31st again). Budget shows each category's upcoming bills for the month, and Fund targets also covers them (upcoming bills less what the category has, or the target's need if larger), earliest date first. Editing a transaction keeps Planner's payment id and bill.

Accounts can be renamed (opening balance and date stay fixed; transfers named "Transfer to <account>" follow the new name), closed at a $0 balance (they move to Closed accounts, keep their transactions and can be reopened) or deleted when they have no transactions. Reconcile offers a cleared "Reconciliation adjustment" into To budget when your bank's cleared balance differs. Targets support monthly refill, a fresh monthly contribution, and a savings balance with an optional due month. Fund Targets assigns existing money in category order, up to the available amount.

Budget rows emphasize Available, with Assigned and Activity as secondary details. Targets show their behavior, funding progress and the amount left to fund this month; the target editor explains each type with an example. Bottom navigation includes icons. Transaction forms adapt to Expense, Income and Category refund; income hides the category, and notes are optional.

Transactions supports payee/category/memo search, income, expenses, category refunds, editing, deleting, and clearing transactions. Accounts show working and cleared balances and support transfers that do not count as spending. Reconcile compares your entered bank balance with the cleared balance; differences must be corrected through transaction records. Reports shows income, net spending after refunds, category breakdowns, and six months of cash flow.

The first upgrade preserves old category balances, cash and transactions and retains a `legacy_backup` snapshot in device preferences. The original version did not record assignment history, so the migration establishes assignments this month; it cannot recreate previous monthly plans accurately.

## Scope

This version supports cash accounts and credit cards and saves locally on the device. It has no bank sync, loans or shared plans. Targets come in three kinds: refill to an amount each month, set aside a fresh amount each month, or save toward a balance (optionally by a due month). Uninstalling or clearing app storage deletes the budget, so keep a backup file (below).

## Backup, restore and export

**Settings > Backup** works through Android's file picker, so MyBudget needs no storage permission.

- **Back up budget** saves `MyBudget-backup-YYYY-MM-DD.json` wherever you choose (Downloads, Drive, a computer). It holds the whole budget: accounts, categories, targets, monthly assignments and every transaction, including the Planner link identifiers. The file is the saved budget JSON plus `app`, `backupVersion` (1) and `created` fields.
- **Restore from backup** reads the whole file first and refuses anything that isn't a readable MyBudget backup, or one from a newer MyBudget, without changing anything. It then shows the backup's date and counts and asks before replacing the budget on this device. The replaced budget is kept on the device, and **Undo restore** in Settings puts it back until the next restore.
- **Export transactions (CSV)** saves every transaction for a spreadsheet: date, payee, category, group, account, transfer to, amount, note, cleared. Text starting with `= + - @` gets a leading `'` so spreadsheets don't run it as a formula. A CSV can't be restored.

- **Automatic backup** (Settings): choose a folder once (Android's folder picker; Drive folders work where the Drive app offers them) and MyBudget saves `MyBudget-auto-YYYY-MM-DD.json` there once a day, keeping the newest 7 and leaving other files alone. It runs as a daily background job and when MyBudget opens; Back up now writes one at once, and Turn off stops it (files already saved stay).
- **Import transactions (CSV)** (Settings): pick a bank statement CSV and match its columns (date, payee, amount, or separate money in and out columns); the columns are guessed from the header and remembered for next time. Dates are read as day/month first (also ISO, `7 Oct 2026` and, when day/month can't read them, US month/day). Rows already in the account (same date, amount and payee), dated in the future, or before the account opened are skipped. Outflows get the category last used with their payee, otherwise a new **To categorize** category; inflows go into To budget. Imported rows are cleared and noted "Imported".
- **Photos** on transactions (e.g. receipts) are kept on this phone (in the app's files, at most 1600 px). Backups hold the budget, not photos: a restore says how many photos aren't on the phone. Photos no transaction uses are deleted when MyBudget starts.

Backups are plain, unencrypted files: keep them somewhere private.

## Calculation checks

Compile `Budget.java`, `CsvImport.java` and `tests/BudgetTest.java` with a JDK, then run `BudgetTest`. Checks cover exact cents, monthly assignments, rollover, overspending, edit/delete recalculation, targets, transfers, cleared balances, refunds, future reservations, the CSV export, deleting and reordering categories, closing and deleting accounts, reconcile adjustments, quick assign amounts and payee suggestions.

Build Android tests with `gradlew.bat assembleDebugAndroidTest`. Install both debug APKs and run `adb shell am instrument -w com.mybudget.app.test/com.mybudget.app.BudgetInstrumentation`. Tests exercise migration, persistence and backup files using Android's real JSON implementation and launch the actual Activity.

## Release signing

`gradlew.bat assembleRelease` creates an unsigned APK. Published APKs are aligned and signed separately with the private MyBudget release key; signing files are excluded from Git and release uploads. Keep a secure backup of the signing key and its password to preserve Android update compatibility, and increment `versionCode` for each future release.

The release certificate SHA-256 fingerprint is `f895800a96ba9478851cf4620d6bcbfc4d1acfa2a1974167c5af69aa0dcd52cd`. Verify the APK download against the `SHA256SUMS.txt` asset included in each release. The certificate fingerprint is public; the signing key and password are private.

Build compatibility reference: [Android Gradle plugin release notes](https://developer.android.com/build/releases/gradle-plugin).
