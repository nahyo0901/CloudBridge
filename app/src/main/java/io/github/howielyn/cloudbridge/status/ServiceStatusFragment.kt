package io.github.howielyn.cloudbridge.status

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ca.pkay.rcloneexplorer.R
import java.io.IOException
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServiceStatusFragment : Fragment() {
    private val repository = StatusRepository()
    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var errorContainer: View? = null
    private var errorDetails: TextView? = null
    private var fallbackRefresh: SwipeRefreshLayout? = null
    private var overallStatus: TextView? = null
    private var updatedAt: TextView? = null
    private var periodGroup: RadioGroup? = null
    private var serviceList: LinearLayout? = null
    private var graphError: TextView? = null
    private var loadJob: Job? = null
    private var graphJob: Job? = null
    private var nativeMode = false
    private var selectedPeriod = StatusPeriod.WEEK
    private var services = emptyList<CloudServiceStatus>()
    private val graphViews = mutableMapOf<String, ImageView>()
    private val periodIds = mutableMapOf<Int, StatusPeriod>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_service_status, container, false)
        progressBar = root.findViewById(R.id.status_progress)
        errorContainer = root.findViewById(R.id.status_error_container)
        errorDetails = root.findViewById(R.id.status_error_details)
        fallbackRefresh = root.findViewById(R.id.status_fallback_refresh)
        overallStatus = root.findViewById(R.id.status_native_overall)
        updatedAt = root.findViewById(R.id.status_native_updated)
        periodGroup = root.findViewById(R.id.status_native_periods)
        serviceList = root.findViewById(R.id.status_native_services)
        graphError = root.findViewById(R.id.status_native_graph_error)

        root.findViewById<View>(R.id.status_retry).setOnClickListener {
            if (nativeMode) {
                loadNativeStatus()
            } else {
                webView?.loadUrl(STATUS_URL)
            }
        }
        fallbackRefresh?.setOnRefreshListener { loadNativeStatus() }
        configurePeriods()
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentWebView = webView
                    if (!nativeMode && currentWebView?.canGoBack() == true) {
                        currentWebView.goBack()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
            showNativeFallback()
        } else {
            try {
                val statusWebView = WebView(requireContext())
                webView = statusWebView
                (view as ViewGroup).addView(
                    statusWebView,
                    0,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                configureWebView(statusWebView)
                statusWebView.loadUrl(STATUS_URL)
            } catch (e: RuntimeException) {
                Log.w(TAG, "WebView is unavailable; using the native status dashboard", e)
                showNativeFallback()
            }
        }
    }

    private fun configurePeriods() {
        val group = periodGroup ?: return
        StatusPeriod.entries.forEach { period ->
            val button = RadioButton(requireContext()).apply {
                id = View.generateViewId()
                text = period.label
                isChecked = period == selectedPeriod
            }
            periodIds[button.id] = period
            group.addView(button)
        }
        group.setOnCheckedChangeListener { _, checkedId ->
            periodIds[checkedId]?.let { period ->
                selectedPeriod = period
                if (services.isNotEmpty()) loadGraphs(period)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(statusWebView: WebView) {
        statusWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        statusWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                errorContainer?.visibility = View.GONE
                progressBar?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar?.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) showNativeFallback()
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                val host = Uri.parse(error.url).host
                if (host == STATUS_HOST || host == RAW_GITHUB_HOST) {
                    showNativeFallback()
                }
            }
        }
        statusWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progressBar?.progress = newProgress
                progressBar?.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }
    }

    private fun showNativeFallback() {
        if (nativeMode) return
        nativeMode = true
        webView?.stopLoading()
        webView?.visibility = View.GONE
        errorContainer?.visibility = View.GONE
        fallbackRefresh?.visibility = View.VISIBLE
        loadNativeStatus()
    }

    private fun loadNativeStatus() {
        loadJob?.cancel()
        graphJob?.cancel()
        webView?.visibility = View.GONE
        fallbackRefresh?.visibility = View.VISIBLE
        fallbackRefresh?.isRefreshing = false
        progressBar?.visibility = View.VISIBLE
        errorContainer?.visibility = View.GONE

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            var graphsStarted = false
            try {
                val latestServices = withContext(Dispatchers.IO) {
                    repository.fetchServices()
                }
                services = latestServices
                renderServices(latestServices)
                updatedAt?.text = getString(
                    R.string.status_native_updated,
                    DateFormat.getTimeFormat(requireContext()).format(Date())
                )
                updateOverallStatus(latestServices)
                loadGraphs(selectedPeriod)
                graphsStarted = true
            } catch (e: IOException) {
                Log.w(TAG, "Could not load compatible status feed", e)
                showError(R.string.status_native_api_error)
            } finally {
                fallbackRefresh?.isRefreshing = false
                if (!graphsStarted) progressBar?.visibility = View.GONE
            }
        }
    }

    private fun renderServices(latestServices: List<CloudServiceStatus>) {
        val list = serviceList ?: return
        list.removeAllViews()
        graphViews.clear()
        latestServices.forEach { service ->
            val card = layoutInflater.inflate(R.layout.item_native_service_status, list, false)
            card.findViewById<TextView>(R.id.native_service_name).text = service.name
            card.findViewById<TextView>(R.id.native_service_state).apply {
                text = stateLabel(service.state)
                setTextColor(statusColor(service.state))
                background = statusBadge(service.state)
            }
            card.findViewById<TextView>(R.id.native_service_uptime).text = getString(
                R.string.status_native_service_uptime,
                service.uptime ?: getString(R.string.status_native_state_unknown)
            )
            card.findViewById<TextView>(R.id.native_service_response_time).text = getString(
                R.string.status_native_response_time,
                service.responseTime(selectedPeriod)?.let(::formatResponseTime)
                    ?: getString(R.string.status_native_state_unknown)
            )
            val graph = card.findViewById<ImageView>(R.id.native_service_graph)
            graph.contentDescription = getString(
                R.string.status_graph_description,
                service.name
            )
            graphViews[service.slug] = graph
            list.addView(card)
        }
    }

    private fun loadGraphs(period: StatusPeriod) {
        val targets = graphViews.toMap()
        if (targets.isEmpty()) {
            progressBar?.visibility = View.GONE
            return
        }
        graphJob?.cancel()
        progressBar?.visibility = View.VISIBLE
        graphJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val results = coroutineScope {
                    targets.map { (slug, imageView) ->
                        async(Dispatchers.IO) {
                            val bytes = try {
                                repository.fetchGraph(slug, period)
                            } catch (e: IOException) {
                                Log.w(TAG, "Could not load response-time graph for $slug", e)
                                null
                            }
                            Triple(slug, imageView, bytes)
                        }
                    }.awaitAll()
                }

                var anyGraphMissing = false
                results.forEach { (_, imageView, bytes) ->
                    val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    imageView.setImageBitmap(bitmap)
                    anyGraphMissing = anyGraphMissing || bitmap == null
                }
                graphError?.visibility = if (anyGraphMissing) View.VISIBLE else View.GONE
                services.forEach { service ->
                    serviceList?.let { list ->
                        for (index in 0 until list.childCount) {
                            val card = list.getChildAt(index)
                            if (card.findViewById<TextView>(R.id.native_service_name).text == service.name) {
                                card.findViewById<TextView>(R.id.native_service_response_time).text =
                                    getString(
                                        R.string.status_native_response_time,
                                        service.responseTime(period)?.let(::formatResponseTime)
                                            ?: getString(R.string.status_native_state_unknown)
                                    )
                            }
                        }
                    }
                }
            } finally {
                progressBar?.visibility = View.GONE
            }
        }
    }

    private fun updateOverallStatus(latestServices: List<CloudServiceStatus>) {
        val overall = when {
            latestServices.any { it.state == ServiceState.DOWN } -> ServiceState.DOWN
            latestServices.any { it.state == ServiceState.DEGRADED } -> ServiceState.DEGRADED
            latestServices.all { it.state == ServiceState.UP } -> ServiceState.UP
            else -> ServiceState.UNKNOWN
        }
        overallStatus?.apply {
            setText(
                when (overall) {
                    ServiceState.UP -> R.string.status_native_overall_up
                    ServiceState.DOWN -> R.string.status_native_overall_down
                    ServiceState.DEGRADED -> R.string.status_native_overall_degraded
                    ServiceState.UNKNOWN -> R.string.status_native_overall_unknown
                }
            )
            setTextColor(statusColor(overall))
        }
    }

    private fun showError(detailsResId: Int) {
        fallbackRefresh?.visibility = View.GONE
        webView?.visibility = View.GONE
        errorDetails?.setText(detailsResId)
        errorContainer?.visibility = View.VISIBLE
        progressBar?.visibility = View.GONE
    }

    private fun stateLabel(state: ServiceState): String = getString(
        when (state) {
            ServiceState.UP -> R.string.status_native_state_up
            ServiceState.DOWN -> R.string.status_native_state_down
            ServiceState.DEGRADED -> R.string.status_native_state_degraded
            ServiceState.UNKNOWN -> R.string.status_native_state_unknown
        }
    )

    private fun statusColor(state: ServiceState): Int = Color.parseColor(
        when (state) {
            ServiceState.UP -> "#2E7D32"
            ServiceState.DOWN -> "#C62828"
            ServiceState.DEGRADED -> "#EF6C00"
            ServiceState.UNKNOWN -> "#616161"
        }
    )

    private fun statusBadge(state: ServiceState) = GradientDrawable().apply {
        setColor(
            Color.parseColor(
                when (state) {
                    ServiceState.UP -> "#1A2E7D32"
                    ServiceState.DOWN -> "#1AC62828"
                    ServiceState.DEGRADED -> "#1AEF6C00"
                    ServiceState.UNKNOWN -> "#1A616161"
                }
            )
        )
        cornerRadius = resources.getDimension(R.dimen.status_badge_corner_radius)
    }

    private fun formatResponseTime(milliseconds: Long): String =
        if (milliseconds < 1000) "$milliseconds ms"
        else String.format(Locale.getDefault(), "%.2f s", milliseconds / 1000.0)

    override fun onDestroyView() {
        loadJob?.cancel()
        graphJob?.cancel()
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        webView = null
        progressBar = null
        errorContainer = null
        errorDetails = null
        fallbackRefresh = null
        overallStatus = null
        updatedAt = null
        periodGroup = null
        serviceList = null
        graphError = null
        graphViews.clear()
        periodIds.clear()
        nativeMode = false
        super.onDestroyView()
    }

    private companion object {
        const val TAG = "ServiceStatusFragment"
        const val STATUS_HOST = "howielyn.github.io"
        const val RAW_GITHUB_HOST = "raw.githubusercontent.com"
        const val STATUS_URL = "https://howielyn.github.io/Cloud-Status/"
    }
}
