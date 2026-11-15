package haven.mobile.core.wallet

data class WalletConfig(
    val projectId: String,
    val appName: String,
    val appDescription: String,
    val appIconUrl: String,
    val redirectUrl: String,
    /** Shown by wallets next to the app name. Not fetched by this app. */
    val appUrl: String = "",
)
