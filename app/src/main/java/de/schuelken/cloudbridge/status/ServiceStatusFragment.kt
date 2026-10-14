package de.schuelken.cloudbridge.status

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import ca.pkay.rcloneexplorer.R
import java.io.IOException
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServiceStatusFragment : Fragment() {
    private val repository = StatusRepository()
    private var loadJob: Job? = null

    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var serviceList: LinearLayout
    private lateinit var loadingIndicator: ProgressBar
    private lateinit var errorMessage: TextView
    private lateinit var overallStatus: TextView
    private lateinit var updatedAt: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_service_status, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refreshLayout = view.findViewById(R.id.status_refresh)
        serviceList = view.findViewById(R.id.status_service_list)
        loadingIndicator = view.findViewById(R.id.status_loading)
        errorMessage = view.findViewById(R.id.status_error)
        overallStatus = view.findViewById(R.id.status_overall)
        updatedAt = view.findViewById(R.id.status_updated)
        refreshLayout.setOnRefreshListener { loadStatus() }
        loadStatus()
    }

    private fun loadStatus() {
        loadJob?.cancel()
        refreshLayout.isRefreshing = serviceList.childCount > 0
        loadingIndicator.visibility = if (serviceList.childCount == 0) View.VISIBLE else View.GONE
        errorMessage.visibility = View.GONE

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val services = withContext(Dispatchers.IO) { repository.fetchServices() }
                renderServices(services)
                updatedAt.text = getString(
                    R.string.status_updated,
                    DateFormat.getTimeFormat(requireContext()).format(Date())
                )
            } catch (e: IOException) {
                errorMessage.visibility = View.VISIBLE
                if (serviceList.childCount == 0) {
                    overallStatus.setText(R.string.status_overall_unknown)
                    overallStatus.setTextColor(statusColor(ServiceState.UNKNOWN))
                }
            } finally {
                refreshLayout.isRefreshing = false
                loadingIndicator.visibility = View.GONE
            }
        }
    }

    private fun renderServices(services: List<CloudServiceStatus>) {
        serviceList.removeAllViews()
        services.forEach { service ->
            val card = layoutInflater.inflate(R.layout.item_service_status, serviceList, false)
            card.findViewById<TextView>(R.id.service_name).text = service.name
            card.findViewById<TextView>(R.id.service_state).apply {
                text = stateLabel(service.state)
                setTextColor(statusColor(service.state))
                background = statusBadge(service.state)
            }
            card.findViewById<TextView>(R.id.service_url).text =
                Uri.parse(service.url).host ?: service.url
            card.findViewById<TextView>(R.id.service_response_time).text = getString(
                R.string.status_response_time,
                service.responseTimeMs?.let(::formatResponseTime)
                    ?: getString(R.string.status_unknown_value)
            )
            card.findViewById<TextView>(R.id.service_uptime).text = getString(
                R.string.status_uptime_periods,
                service.uptimeDay ?: getString(R.string.status_unknown_value),
                service.uptimeWeek ?: getString(R.string.status_unknown_value),
                service.uptimeMonth ?: getString(R.string.status_unknown_value),
                service.uptimeYear ?: getString(R.string.status_unknown_value)
            )
            card.findViewById<TextView>(R.id.service_action).setText(R.string.status_open_service)
            card.contentDescription = getString(R.string.status_open_service_named, service.name)
            card.setOnClickListener { openService(service) }
            serviceList.addView(card)
        }

        val overall = when {
            services.any { it.state == ServiceState.DOWN } -> ServiceState.DOWN
            services.any { it.state == ServiceState.DEGRADED } -> ServiceState.DEGRADED
            services.all { it.state == ServiceState.UP } -> ServiceState.UP
            else -> ServiceState.UNKNOWN
        }
        overallStatus.setText(
            when (overall) {
                ServiceState.UP -> R.string.status_overall_up
                ServiceState.DOWN -> R.string.status_overall_down
                ServiceState.DEGRADED -> R.string.status_overall_degraded
                ServiceState.UNKNOWN -> R.string.status_overall_unknown
            }
        )
        overallStatus.setTextColor(statusColor(overall))
        errorMessage.visibility = View.GONE
    }

    private fun openService(service: CloudServiceStatus) {
        val uri = Uri.parse(service.url)
        if (uri.scheme != "https" && uri.scheme != "http") return
        val intent = Intent(Intent.ACTION_VIEW, uri)
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), R.string.status_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    private fun stateLabel(state: ServiceState): String = getString(
        when (state) {
            ServiceState.UP -> R.string.status_state_up
            ServiceState.DOWN -> R.string.status_state_down
            ServiceState.DEGRADED -> R.string.status_state_degraded
            ServiceState.UNKNOWN -> R.string.status_unknown_value
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
        setColor(Color.parseColor(
            when (state) {
                ServiceState.UP -> "#1A2E7D32"
                ServiceState.DOWN -> "#1AC62828"
                ServiceState.DEGRADED -> "#1AEF6C00"
                ServiceState.UNKNOWN -> "#1A616161"
            }
        ))
        cornerRadius = resources.getDimension(R.dimen.status_badge_corner_radius)
    }

    private fun formatResponseTime(milliseconds: Long): String =
        if (milliseconds < 1000) "$milliseconds ms"
        else String.format(java.util.Locale.getDefault(), "%.2f s", milliseconds / 1000.0)
}
