package com.pedidosexpress

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {
    private lateinit var apiService: ApiService
    private lateinit var authService: AuthService
    private lateinit var progressBar: View
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var ordersTodayText: TextView
    private lateinit var revenueTodayText: TextView
    private lateinit var ordersWeekText: TextView
    private lateinit var revenueWeekText: TextView
    private lateinit var avgTicketText: TextView
    private lateinit var pendingOrdersText: TextView
    private lateinit var chartOrders: BarChart
    private lateinit var chartRevenue: LineChart
    
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            loadStats(true)
            handler.postDelayed(this, 30000) // Atualizar a cada 30 segundos
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        apiService = ApiService(requireContext())
        authService = AuthService(requireContext())
        
        // Atualizar labels dinamicamente (se os IDs existirem no layout)
        val user = authService.getUser()
        // Nota: Os labels estão hardcoded no XML, mas podem ser atualizados programaticamente se necessário
        
        progressBar = view.findViewById(R.id.progress_bar)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        ordersTodayText = view.findViewById(R.id.orders_today)
        revenueTodayText = view.findViewById(R.id.revenue_today)
        ordersWeekText = view.findViewById(R.id.orders_week)
        revenueWeekText = view.findViewById(R.id.revenue_week)
        avgTicketText = view.findViewById(R.id.avg_ticket)
        pendingOrdersText = view.findViewById(R.id.pending_orders)
        chartOrders = view.findViewById(R.id.chart_orders)
        chartRevenue = view.findViewById(R.id.chart_revenue)
        
        setupCharts()
        
        swipeRefresh.setOnRefreshListener {
            loadStats(false)
        }
        
        loadStats(false)
        handler.postDelayed(refreshRunnable, 30000)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(refreshRunnable)
    }
    
    private fun loadStats(silent: Boolean) {
        if (!silent) {
            progressBar.visibility = View.VISIBLE
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val stats = withContext(Dispatchers.IO) {
                    apiService.getStats()
                }
                if (!isAdded) return@launch
                applyStatsToUi(stats)
            } catch (e: Exception) {
                android.util.Log.e("DashboardFragment", "Erro ao carregar stats", e)
                if (isAdded) {
                    applyStatsToUi(DashboardStats(
                        today = DayStats(0, 0.0),
                        week = WeekStats(0, 0.0),
                        pendingOrders = 0,
                        dailyStats = emptyList()
                    ))
                    if (!silent) {
                        Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                if (isAdded) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    private fun applyStatsToUi(stats: DashboardStats) {
        ordersTodayText.text = "${stats.today.orders}"
        revenueTodayText.text = "R$ ${String.format(java.util.Locale("pt", "BR"), "%.2f", stats.today.revenue)}"
        ordersWeekText.text = "${stats.week.orders}"
        revenueWeekText.text = "R$ ${String.format(java.util.Locale("pt", "BR"), "%.2f", stats.week.revenue)}"
        pendingOrdersText.text = "${stats.pendingOrders}"
        val avgTicket = if (stats.today.orders > 0) stats.today.revenue / stats.today.orders else 0.0
        avgTicketText.text = "R$ ${String.format(java.util.Locale("pt", "BR"), "%.2f", avgTicket)}"
        updateCharts(stats.dailyStats)
    }
    
    private fun setupCharts() {
        // Configurar gráfico de barras (pedidos)
        chartOrders.description.isEnabled = false
        chartOrders.setTouchEnabled(true)
        chartOrders.setDragEnabled(true)
        chartOrders.setScaleEnabled(false)
        chartOrders.setPinchZoom(false)
        chartOrders.setDrawGridBackground(false)
        chartOrders.legend.isEnabled = false
        
        val xAxisOrders = chartOrders.xAxis
        xAxisOrders.position = XAxis.XAxisPosition.BOTTOM
        xAxisOrders.setDrawGridLines(false)
        xAxisOrders.textColor = Color.parseColor("#6b7280")
        xAxisOrders.textSize = 11f
        
        val leftAxisOrders = chartOrders.axisLeft
        leftAxisOrders.setDrawGridLines(true)
        leftAxisOrders.gridColor = Color.parseColor("#f3f4f6")
        leftAxisOrders.textColor = Color.parseColor("#6b7280")
        leftAxisOrders.axisMinimum = 0f
        
        chartOrders.axisRight.isEnabled = false
        
        // Configurar gráfico de linhas (receita)
        chartRevenue.description.isEnabled = false
        chartRevenue.setTouchEnabled(true)
        chartRevenue.setDragEnabled(true)
        chartRevenue.setScaleEnabled(false)
        chartRevenue.setPinchZoom(false)
        chartRevenue.setDrawGridBackground(false)
        chartRevenue.legend.isEnabled = false
        
        val xAxisRevenue = chartRevenue.xAxis
        xAxisRevenue.position = XAxis.XAxisPosition.BOTTOM
        xAxisRevenue.setDrawGridLines(false)
        xAxisRevenue.textColor = Color.parseColor("#6b7280")
        xAxisRevenue.textSize = 11f
        
        val leftAxisRevenue = chartRevenue.axisLeft
        leftAxisRevenue.setDrawGridLines(true)
        leftAxisRevenue.gridColor = Color.parseColor("#f3f4f6")
        leftAxisRevenue.textColor = Color.parseColor("#6b7280")
        leftAxisRevenue.axisMinimum = 0f
        
        chartRevenue.axisRight.isEnabled = false
    }
    
    private fun updateCharts(dailyStats: List<DailyStat>) {
        val list = if (dailyStats.isEmpty()) {
            // Semana com zeros para os gráficos não ficarem vazios
            listOf("Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom").map { day ->
                DailyStat(day = day, orders = 0, revenue = 0.0)
            }
        } else {
            dailyStats
        }
        val labels = list.map { it.day.take(3) }
        // Gráfico de barras - Pedidos
        val entriesOrders = list.mapIndexed { index, stat ->
            BarEntry(index.toFloat(), stat.orders.toFloat())
        }
        val user = authService.getUser()
        val barDataSet = BarDataSet(entriesOrders, BusinessTypeHelper.ordersLabel(user))
        barDataSet.color = Color.parseColor("#ea580c")
        barDataSet.valueTextColor = Color.parseColor("#111827")
        barDataSet.valueTextSize = 10f
        chartOrders.data = BarData(barDataSet)
        chartOrders.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chartOrders.invalidate()
        // Gráfico de linhas - Receita
        val entriesRevenue = list.mapIndexed { index, stat ->
            Entry(index.toFloat(), stat.revenue.toFloat())
        }
        val lineDataSet = LineDataSet(entriesRevenue, "Receita")
        lineDataSet.color = Color.parseColor("#16a34a")
        lineDataSet.setCircleColor(Color.parseColor("#16a34a"))
        lineDataSet.lineWidth = 3f
        lineDataSet.circleRadius = 5f
        lineDataSet.setDrawCircleHole(false)
        lineDataSet.valueTextColor = Color.parseColor("#111827")
        lineDataSet.valueTextSize = 10f
        lineDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        lineDataSet.setDrawFilled(true)
        lineDataSet.fillColor = Color.parseColor("#16a34a")
        lineDataSet.fillAlpha = 50
        chartRevenue.data = LineData(lineDataSet)
        chartRevenue.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        chartRevenue.invalidate()
    }
}
