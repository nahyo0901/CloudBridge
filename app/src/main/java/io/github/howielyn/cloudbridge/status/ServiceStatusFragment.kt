package io.github.howielyn.cloudbridge.status

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.MarkdownView

class ServiceStatusFragment : Fragment() {
    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var errorContainer: View? = null
    private var errorDetails: TextView? = null
    private var secureConnectionFailed = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = inflater.inflate(R.layout.fragment_service_status, container, false) as FrameLayout
        val statusWebView = try {
            WebView(webViewContext(requireContext()))
        } catch (e: Exception) {
            MarkdownView.closeOnMissingWebView(requireActivity(), e)
            return root
        }

        webView = statusWebView
        progressBar = root.findViewById(R.id.status_progress)
        errorContainer = root.findViewById(R.id.status_error_container)
        errorDetails = root.findViewById(R.id.status_error_details)

        root.addView(
            statusWebView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        root.findViewById<Button>(R.id.status_retry).setOnClickListener {
            errorContainer?.visibility = View.GONE
            statusWebView.loadUrl(STATUS_URL)
        }

        configureWebView(statusWebView)
        statusWebView.loadUrl(STATUS_URL)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val currentWebView = webView
                    if (currentWebView?.canGoBack() == true) {
                        currentWebView.goBack()
                    } else {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(statusWebView: WebView) {
        statusWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        statusWebView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                secureConnectionFailed = false
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
                if (request.isForMainFrame && !secureConnectionFailed) {
                    showError(R.string.status_web_connection_error)
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
                if (Uri.parse(error.url).host == STATUS_HOST) {
                    secureConnectionFailed = true
                    showError(R.string.status_web_ssl_error)
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

    private fun showError(detailsResId: Int) {
        errorDetails?.setText(detailsResId)
        errorContainer?.visibility = View.VISIBLE
        progressBar?.visibility = View.GONE
    }

    private fun webViewContext(context: Context): Context =
        if (Build.VERSION.SDK_INT == 22 || Build.VERSION.SDK_INT == 23) {
            context.createConfigurationContext(Configuration())
        } else {
            context
        }

    override fun onDestroyView() {
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
        super.onDestroyView()
    }

    private companion object {
        const val STATUS_HOST = "howielyn.github.io"
        const val STATUS_URL = "https://howielyn.github.io/Cloud-Status/"
    }
}
