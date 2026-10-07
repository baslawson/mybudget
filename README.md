# MyBudget for Android

A native, offline envelope budgeting app with an original interface, informed by YNAB's official method and product documentation. Give the money you have a purpose: record income, assign it to categories, track expenses, and move money to cover overspending.

## Project notes — 7 October 2026

- Native Android app built with Java and Android framework widgets, with no third-party runtime libraries. Android 8+; AUD; local device storage.
- MyBudget branding uses an indigo envelope with a mint checkmark, a matching adaptive launcher icon, and the tagline “Your money. Your plan.” All artwork is vector-based.
- Navigation: Home, Plan, Spending, Accounts and Reflect. The three-dot options menu opens Settings; theme selection lives under Settings > Theme.
- Light, Dark and Auto appearances persist across restarts. Auto follows the device theme, including changes while the app is open. Settings returns to the previous screen.
- Latest UI refinements: compact category rows with Available emphasized; secondary Assigned/Activity values; target descriptions, progress and remaining funding; matching navigation icons; tighter spacing with comfortable touch targets.
- Transaction forms adapt to expense, income and refund. Income hides the category and uses an income-source prompt. Notes are optional. Target editing explains each behavior and shows a deadline only for balance goals.
- Latest debug APK: **76,243 bytes** (76.2 KB / 74.5 KiB), measured from `app/build/outputs/apk/debug/app-debug.apk`. This is a development build; size may change with subsequent builds.
- Verification completed: APK build; calculation checks; Android migration/persistence checks; all five tabs rendered; visual inspection; theme persistence and system-theme switching; income field visibility; conditional target deadline. Checks used the Pixel 7 Pro emulator. Physical-device verification remains outstanding.
- Existing budget data is retained during APK updates. No bank sync, credit-card handling or cloud/export backup is implemented.

## Working agreement

Before future coding, present a plan describing the proposed changes and wait for the user's approval. Implement only after approval. The user likes the current visual direction; preserve the branding and overall style when refining it.

## Run

Open this folder in Android Studio, let Gradle sync, and run `app` on an emulator or Android device (Android 8 or newer). Use JDK 17 or 21 for Gradle and install Android SDK 35. Build a debug APK with `gradlew.bat assembleDebug`; the output is `app/build/outputs/apk/debug/app-debug.apk`.

## First budget

Open the **three-dot menu > Settings > Theme** to choose **Light**, **Dark**, or **Auto (follow device)**. The choice is saved across restarts. Auto follows Android's system light/dark setting, including changes while the app is open. Switching appearance preserves the selected screen, month and search; Back from Settings returns to your previous screen.

1. Add cash, checking and savings accounts with their current opening balances.
2. Tap each category to assign some of the money ready to assign.
3. Record expenses against the category they belong to.
4. Move money from another category when an envelope is overspent.

Amounts are AUD and stored as integer cents. Each month's Plan displays Assigned, Activity and Available for grouped categories. Positive available balances roll forward. Uncovered cash overspending resets the category at rollover and reduces the following month's Ready to Assign. Transactions are recorded from an account's opening date onward. Future assignments reserve existing money; future income is never assumed.

Tap categories to assign or return money, move money, edit their group or target, or view transactions. Targets support monthly refill, a fresh monthly contribution, and a savings balance with an optional due month. Fund Targets assigns existing money in category order, up to the available amount.

Plan rows emphasize Available, with Assigned and Activity as secondary details. Targets show their behavior, funding progress and the amount left to fund this month; the target editor explains each type with an example. Bottom navigation includes icons. Transaction forms adapt to Expense, Income and Category refund; income hides the category, and notes are optional.

Spending supports payee/category/memo search, income, expenses, category refunds, editing, deleting, and clearing transactions. Accounts show working and cleared balances and support transfers that do not count as spending. Reconcile compares your entered bank balance with the cleared balance; differences must be corrected through transaction records. Reflect shows income, net spending after refunds, category breakdowns, and six months of cash flow.

The first upgrade preserves old category balances, cash and transactions and retains a `legacy_backup` snapshot in device preferences. The original version did not record assignment history, so the migration establishes assignments this month; it cannot recreate previous monthly plans accurately.

## Scope

This version supports multiple cash accounts and saves locally on the device. It has no bank sync, credit-card/debt handling, scheduled or split transactions, shared plans, or cloud/export backup yet. Target types are a subset of YNAB's options. Uninstalling or clearing app storage deletes the budget.

## Calculation checks

Compile `Budget.java` and `tests/BudgetTest.java` with a JDK, then run `BudgetTest`. Checks cover exact cents, monthly assignments, rollover, overspending, edit/delete recalculation, targets, transfers, cleared balances, refunds and future reservations.

Build Android tests with `gradlew.bat assembleDebugAndroidTest`. Install both debug APKs and run `adb shell am instrument -w com.mybudget.app.test/com.mybudget.app.BudgetInstrumentation`. Tests exercise migration and persistence using Android's real JSON implementation and launch the actual Activity.

## Design references

- [YNAB setup and method](https://www.ynab.com/guide/the-ultimate-get-started-guide)
- [Monthly accounting glossary](https://support.ynab.com/en_us/ynab-glossary-a-guide-BJd80SORq)
- [Funding targets](https://support.ynab.com/how-to-use-targets-rk5kkI9ks)
- [Mobile organization](https://www.ynab.com/whats-new/the-great-ynab-remodel)
- [Overspending and future assignments](https://support.ynab.com/en_us/troubleshooting-your-plan-r19HPofJo)

Build compatibility reference: [Android Gradle plugin release notes](https://developer.android.com/build/releases/gradle-plugin).
