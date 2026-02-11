package com.pedidosexpress

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {
    
    private lateinit var apiService: ApiService
    private lateinit var isOpenSwitch: Switch
    private lateinit var storeClosedMessage: EditText
    private lateinit var saveStoreButton: Button
    private lateinit var storeProgressBar: ProgressBar
    
    // Subscription views
    private lateinit var subscriptionCard: CardView
    private lateinit var subscriptionPlanName: TextView
    private lateinit var subscriptionPaymentDate: TextView
    private lateinit var subscriptionExpiresAt: TextView
    private lateinit var subscriptionWarningCard: CardView
    private lateinit var subscriptionWarningText: TextView
    private lateinit var subscriptionPaymentButton: Button
    private lateinit var subscriptionProgressBar: ProgressBar
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val authService = AuthService(requireContext())
        val user = authService.getUser()
        
        // Informações do usuário
        view.findViewById<TextView>(R.id.user_name).text = user?.name ?: "N/A"
        view.findViewById<TextView>(R.id.user_role).text = when(user?.role) {
            "admin" -> "Administrador"
            "manager" -> "Gerente"
            else -> user?.role ?: "N/A"
        }
        view.findViewById<TextView>(R.id.user_email).text = user?.username ?: "N/A"
        
        // Status da Loja
        apiService = ApiService(requireContext())
        isOpenSwitch = view.findViewById(R.id.is_open_switch)
        storeClosedMessage = view.findViewById(R.id.store_closed_message)
        saveStoreButton = view.findViewById(R.id.save_store_button)
        storeProgressBar = view.findViewById(R.id.store_progress_bar)
        
        val storeClosedHint = view.findViewById<TextView>(R.id.store_closed_hint)
        isOpenSwitch.setOnCheckedChangeListener { _, isChecked ->
            val visible = !isChecked
            storeClosedHint.visibility = if (visible) View.VISIBLE else View.GONE
            storeClosedMessage.visibility = if (visible) View.VISIBLE else View.GONE
        }
        
        saveStoreButton.setOnClickListener {
            saveStoreStatus()
        }
        
        loadStoreStatus()
        
        // Subscription views
        subscriptionCard = view.findViewById(R.id.subscription_card)
        subscriptionPlanName = view.findViewById(R.id.subscription_plan_name)
        subscriptionPaymentDate = view.findViewById(R.id.subscription_payment_date)
        subscriptionExpiresAt = view.findViewById(R.id.subscription_expires_at)
        subscriptionWarningCard = view.findViewById(R.id.subscription_warning_card)
        subscriptionWarningText = view.findViewById(R.id.subscription_warning_text)
        subscriptionPaymentButton = view.findViewById(R.id.subscription_payment_button)
        subscriptionProgressBar = view.findViewById(R.id.subscription_progress_bar)
        
        subscriptionPaymentButton.setOnClickListener {
            openPaymentPage()
        }
        
        loadSubscription()
        
        // Logout
        view.findViewById<Button>(R.id.logout_button).setOnClickListener {
            authService.logout()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }
    
    private fun loadStoreStatus() {
        storeProgressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    apiService.getStoreStatus()
                }
                
                isOpenSwitch.isChecked = status.isOpen
                storeClosedMessage.setText(status.message ?: "")
                val closedVisible = !status.isOpen
                requireView().findViewById<TextView>(R.id.store_closed_hint).visibility = if (closedVisible) View.VISIBLE else View.GONE
                storeClosedMessage.visibility = if (closedVisible) View.VISIBLE else View.GONE
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Erro ao carregar status", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                storeProgressBar.visibility = View.GONE
            }
        }
    }
    
    private fun saveStoreStatus() {
        storeProgressBar.visibility = View.VISIBLE
        saveStoreButton.isEnabled = false
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val message = if (isOpenSwitch.isChecked) null else storeClosedMessage.text?.toString()?.trim()
                withContext(Dispatchers.IO) {
                    apiService.updateStoreStatus(isOpenSwitch.isChecked, message = message)
                }
                
                Toast.makeText(requireContext(), "Status atualizado!", Toast.LENGTH_SHORT).show()
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Erro ao salvar status", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                storeProgressBar.visibility = View.GONE
                saveStoreButton.isEnabled = true
            }
        }
    }
    
    private fun loadSubscription() {
        subscriptionProgressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val subscription = withContext(Dispatchers.IO) {
                    apiService.getSubscription()
                }
                
                if (subscription == null) {
                    subscriptionCard.visibility = View.GONE
                    return@launch
                }
                
                subscriptionCard.visibility = View.VISIBLE
                
                // Exibir plano
                subscriptionPlanName.text = subscription.planName
                
                // Exibir data de pagamento
                subscriptionPaymentDate.text = if (subscription.paymentDate != null) {
                    formatDate(subscription.paymentDate)
                } else {
                    "Não informado"
                }
                
                // Exibir data de vencimento
                subscriptionExpiresAt.text = if (subscription.expiresAt != null) {
                    formatDate(subscription.expiresAt)
                } else {
                    "Não informado"
                }
                
                // Verificar vencimento e mostrar aviso
                if (subscription.isExpired) {
                    subscriptionWarningCard.visibility = View.VISIBLE
                    subscriptionWarningText.text = "⚠️ Assinatura vencida há ${Math.abs(subscription.daysUntilExpiration ?: 0)} dia(s). Renove agora!"
                    subscriptionWarningCard.setCardBackgroundColor(0xFFFEE2E2.toInt()) // Vermelho claro
                    subscriptionPaymentButton.visibility = View.VISIBLE
                } else if (subscription.isExpiringSoon) {
                    subscriptionWarningCard.visibility = View.VISIBLE
                    val days = subscription.daysUntilExpiration ?: 0
                    subscriptionWarningText.text = "⚠️ Sua assinatura vence em $days dia(s). Renove agora para continuar usando o sistema!"
                    subscriptionWarningCard.setCardBackgroundColor(0xFFFEF3C7.toInt()) // Amarelo claro
                    subscriptionPaymentButton.visibility = View.VISIBLE
                } else {
                    subscriptionWarningCard.visibility = View.GONE
                    subscriptionPaymentButton.visibility = View.GONE
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Erro ao carregar assinatura", e)
                // Não mostrar erro para não incomodar o usuário
                subscriptionCard.visibility = View.GONE
            } finally {
                subscriptionProgressBar.visibility = View.GONE
            }
        }
    }
    
    private fun formatDate(dateString: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(dateString)
            
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            dateString
        }
    }
    
    private fun openPaymentPage() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val subscription = withContext(Dispatchers.IO) {
                    apiService.getSubscription()
                }
                
                if (subscription != null && subscription.paymentUrl.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(subscription.paymentUrl))
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "URL de pagamento não disponível", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsFragment", "Erro ao abrir página de pagamento", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
