package com.example.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.functions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.util.Currency

/** One subscription option shown on the VIP sheet (a base plan of the `snug_vip` subscription). */
data class VipPlan(
    val basePlanId: String,
    val months: Int,
    val priceText: String,
    val perMonthText: String,
    val trialDays: Int,
    val offerToken: String,
    internal val details: ProductDetails
)

/**
 * Google Play Billing for SNUG VIP.
 *
 * The phone never decides who is VIP. After a purchase it sends the purchase token to the
 * `verifyPurchase` Cloud Function, which checks it with Google and sets `isVip` on the user.
 * [onEntitlementChanged] is called when the server confirmed a change so the profile can reload.
 */
class BillingManager(
    context: Context,
    private val scope: CoroutineScope,
    private val onEntitlementChanged: () -> Unit
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "snug_vip"
        private const val TAG = "BillingManager"
    }

    private val appContext = context.applicationContext

    private val _plans = MutableStateFlow<List<VipPlan>>(emptyList())
    val plans: StateFlow<List<VipPlan>> = _plans.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun clearMessage() { _message.value = null }

    /** Connects to Google Play, loads prices and re-checks purchases that were never verified. */
    fun start() {
        if (client.isReady) {
            scope.launch { loadPlans(); restore(silent = true) }
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    scope.launch { loadPlans(); restore(silent = true) }
                } else {
                    Log.w(TAG, "Billing setup failed: ${result.debugMessage}")
                    _loading.value = false
                }
            }

            override fun onBillingServiceDisconnected() {
                // enableAutoServiceReconnection() reconnects on the next call
            }
        })
    }

    private suspend fun loadPlans() {
        _loading.value = true
        try {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build()
                    )
                ).build()
            val result = client.queryProductDetails(params)
            val details = result.productDetailsList?.firstOrNull()
            _plans.value = if (details == null) emptyList() else buildPlans(details)
        } catch (e: Exception) {
            Log.w(TAG, "loadPlans failed", e)
            _plans.value = emptyList()
        } finally {
            _loading.value = false
        }
    }

    private fun buildPlans(details: ProductDetails): List<VipPlan> {
        val offers = details.subscriptionOfferDetails ?: return emptyList()
        return offers.groupBy { it.basePlanId }.mapNotNull { (basePlanId, group) ->
            // Prefer an offer with a free trial (Play only returns trials this user may still use)
            val trialOffer = group.firstOrNull { o -> o.pricingPhases.pricingPhaseList.firstOrNull()?.priceAmountMicros == 0L }
            val offer = trialOffer ?: group.firstOrNull { it.offerId == null } ?: group.first()
            val phases = offer.pricingPhases.pricingPhaseList
            val recurring = phases.lastOrNull() ?: return@mapNotNull null
            val months = periodMonths(recurring.billingPeriod)
            val trialDays = if (phases.size > 1 && phases.first().priceAmountMicros == 0L) periodDays(phases.first().billingPeriod) else 0
            VipPlan(
                basePlanId = basePlanId,
                months = months,
                priceText = recurring.formattedPrice,
                perMonthText = perMonth(recurring.priceAmountMicros, recurring.priceCurrencyCode, months),
                trialDays = trialDays,
                offerToken = offer.offerToken,
                details = details
            )
        }.sortedBy { it.months }
    }

    fun launchPurchase(activity: Activity, plan: VipPlan) {
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            _message.value = appContext.getString(com.example.R.string.billing_sign_in_again)
            return
        }
        _busy.value = true
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(plan.details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            )
            // Lets the server prove this purchase belongs to this account
            .setObfuscatedAccountId(uid)
            .build()
        val result = client.launchBillingFlow(activity, flow)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _busy.value = false
            _message.value = appContext.getString(com.example.R.string.billing_open_play_failed)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach { p -> scope.launch { handlePurchase(p, silent = false) } } ?: run { _busy.value = false }
            BillingClient.BillingResponseCode.USER_CANCELED -> _busy.value = false
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch { restore(silent = false) }
            }
            else -> {
                _busy.value = false
                _message.value = appContext.getString(com.example.R.string.billing_purchase_failed)
            }
        }
    }

    /** Re-sends owned purchases to the server (restore purchases / unfinished purchases after a crash). */
    suspend fun restore(silent: Boolean) {
        try {
            val result = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            )
            val owned = result.purchasesList.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            if (owned.isEmpty()) {
                _busy.value = false
                if (!silent) _message.value = appContext.getString(com.example.R.string.billing_no_active_subscription)
                return
            }
            owned.forEach { handlePurchase(it, silent) }
        } catch (e: Exception) {
            Log.w(TAG, "restore failed", e)
            _busy.value = false
            if (!silent) _message.value = appContext.getString(com.example.R.string.billing_check_purchases_failed)
        }
    }

    private suspend fun handlePurchase(purchase: Purchase, silent: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                _busy.value = false
                _message.value = appContext.getString(com.example.R.string.billing_payment_pending)
            }
            Purchase.PurchaseState.PURCHASED -> verifyWithServer(purchase, silent)
            else -> _busy.value = false
        }
    }

    private suspend fun verifyWithServer(purchase: Purchase, silent: Boolean) {
        try {
            val response = Firebase.functions.getHttpsCallable("verifyPurchase").call(
                hashMapOf(
                    "purchaseToken" to purchase.purchaseToken,
                    "productId" to (purchase.products.firstOrNull() ?: PRODUCT_ID)
                )
            ).await()
            val active = (response.data as? Map<*, *>)?.get("active") == true
            if (active) {
                onEntitlementChanged()
                if (!silent) _message.value = appContext.getString(com.example.R.string.billing_welcome_vip)
            } else if (!silent) {
                _message.value = appContext.getString(com.example.R.string.billing_subscription_inactive)
            }
        } catch (e: Exception) {
            Log.w(TAG, "verifyPurchase failed", e)
            // The purchase stays unacknowledged, so it is retried at the next app start
            if (!silent) _message.value = appContext.getString(com.example.R.string.billing_purchase_confirmation_pending)
        } finally {
            _busy.value = false
        }
    }

    // ---- helpers ----

    private fun periodMonths(iso: String): Int {
        val m = Regex("P(\\d+)([DWMY])").matchEntire(iso) ?: return 1
        val n = m.groupValues[1].toInt()
        return when (m.groupValues[2]) {
            "Y" -> n * 12
            "M" -> n
            "W" -> maxOf(1, n / 4)
            else -> maxOf(1, n / 30)
        }
    }

    private fun periodDays(iso: String): Int {
        val m = Regex("P(\\d+)([DWMY])").matchEntire(iso) ?: return 0
        val n = m.groupValues[1].toInt()
        return when (m.groupValues[2]) {
            "Y" -> n * 365
            "M" -> n * 30
            "W" -> n * 7
            else -> n
        }
    }

    private fun perMonth(micros: Long, currencyCode: String, months: Int): String = try {
        val fmt = NumberFormat.getCurrencyInstance().apply { currency = Currency.getInstance(currencyCode) }
        fmt.format(micros / 1_000_000.0 / months)
    } catch (e: Exception) {
        ""
    }

    fun end() { if (client.isReady) client.endConnection() }
}
