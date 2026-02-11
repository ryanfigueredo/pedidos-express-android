package com.pedidosexpress

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class SupportFragment : Fragment() {
    
    companion object {
        const val ARG_OPEN_PHONE = "open_phone"
        const val ARG_OPEN_CUSTOMER_NAME = "open_customer_name"
    }
    
    private lateinit var apiService: ApiService
    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var whatsappButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var emptyStateView: View
    private lateinit var chatContainer: View
    private lateinit var titleAtendimento: TextView
    
    private var conversations = mutableListOf<PriorityConversation>()
    private var selectedConversation: PriorityConversation? = null
    private var bottomNavigation: com.google.android.material.bottomnavigation.BottomNavigationView? = null
    private var messages = mutableListOf<ChatMessage>()
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var lastKeypadVisible = false
    private var keyboardLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        apiService = ApiService(requireContext())
        
        conversationsRecyclerView = view.findViewById(R.id.conversations_recycler)
        messagesRecyclerView = view.findViewById(R.id.messages_recycler)
        messageInput = view.findViewById(R.id.message_input)
        sendButton = view.findViewById(R.id.send_button)
        whatsappButton = view.findViewById(R.id.whatsapp_button)
        backButton = view.findViewById(R.id.back_button)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        emptyStateView = view.findViewById(R.id.empty_state)
        chatContainer = view.findViewById(R.id.chat_container)
        titleAtendimento = view.findViewById(R.id.title_atendimento)
        
        conversationsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        messagesRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        conversationsRecyclerView.adapter = ConversationsAdapter(conversations) { conversation ->
            selectConversation(conversation)
        }
        
        messagesRecyclerView.adapter = MessagesAdapter(messages)
        
        sendButton.setOnClickListener {
            sendMessage()
        }
        
        whatsappButton.setOnClickListener {
            openWhatsApp()
        }
        
        backButton.setOnClickListener {
            goBackToConversationsList()
        }
        
        messageInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                sendButton.isEnabled = !s.toString().trim().isEmpty()
            }
        })
        
        swipeRefresh.setOnRefreshListener {
            loadConversations()
        }
        
        view.findViewById<Button>(R.id.btn_iniciar_atendimento).setOnClickListener {
            showIniciarAtendimentoDialog()
        }
        
        titleAtendimento.visibility = View.VISIBLE
        emptyStateView.visibility = View.VISIBLE
        chatContainer.visibility = View.GONE
        
        // Buscar referência ao bottom navigation da activity
        activity?.let {
            if (it is MainNavigationActivity) {
                bottomNavigation = it.findViewById(R.id.bottom_navigation)
            }
        }
        
        // Detectar quando o teclado abre/fecha e esconder/mostrar bottom nav
        setupKeyboardListener(view)
        
        loadConversations()
        startAutoRefresh()
    }
    
    private fun setupKeyboardListener(rootView: View) {
        val thresholdPx = 200.dpToPx(requireContext())
        keyboardLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            if (!isAdded || context == null) return@OnGlobalLayoutListener
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom
            val keypadVisible = keypadHeight > thresholdPx
            // Só alterar a bottom nav quando o estado do teclado mudar (evita relayout a cada frame e bug ao digitar)
            if (keypadVisible != lastKeypadVisible) {
                lastKeypadVisible = keypadVisible
                bottomNavigation?.visibility = if (keypadVisible) View.GONE else View.VISIBLE
            }
        }
        rootView.viewTreeObserver.addOnGlobalLayoutListener(keyboardLayoutListener!!)
    }
    
    private fun Int.dpToPx(context: android.content.Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
    
    override fun onDestroyView() {
        keyboardLayoutListener?.let { listener ->
            view?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
        }
        keyboardLayoutListener = null
        stopAutoRefresh()
        super.onDestroyView()
    }
    
    private fun loadConversations() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val loadedConversations = withContext(Dispatchers.IO) {
                    apiService.getInboxConversations()
                }
                
                conversations.clear()
                conversations.addAll(loadedConversations)
                
                (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
                
                if (conversations.isEmpty()) {
                    emptyStateView.visibility = View.VISIBLE
                    chatContainer.visibility = View.GONE
                    titleAtendimento.visibility = View.VISIBLE
                } else {
                    emptyStateView.visibility = View.GONE
                    if (selectedConversation == null) {
                        chatContainer.visibility = View.GONE
                        titleAtendimento.visibility = View.VISIBLE
                    } else {
                        chatContainer.visibility = View.VISIBLE
                        titleAtendimento.visibility = View.GONE
                    }
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SupportFragment", "Erro ao carregar conversas", e)
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
                conversations.clear()
                (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
                emptyStateView.visibility = View.VISIBLE
                chatContainer.visibility = View.GONE
                titleAtendimento.visibility = View.VISIBLE
            } finally {
                swipeRefresh.isRefreshing = false
                // Sempre abrir chat com o número do cliente quando veio do botão Atendimento do pedido
                val openPhone = arguments?.getString(ARG_OPEN_PHONE)
                if (!openPhone.isNullOrEmpty()) {
                    arguments?.remove(ARG_OPEN_PHONE)
                    arguments?.remove(ARG_OPEN_CUSTOMER_NAME)
                    val openNormalized = normalizePhoneForCompare(openPhone)
                    val existing = conversations.find { normalizePhoneForCompare(it.phone) == openNormalized }
                    val conv = existing ?: PriorityConversation(
                        phone = openPhone,
                        phoneFormatted = openPhone,
                        whatsappUrl = "",
                        waitTime = 0,
                        timestamp = System.currentTimeMillis(),
                        lastMessage = 0
                    )
                    if (existing == null) {
                        conversations.add(0, conv)
                        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
                    }
                    selectConversationFromOrder(conv)
                }
            }
        }
    }
    
    private fun showIniciarAtendimentoDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Número do cliente (ex: 71999999999)"
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Iniciar atendimento")
            .setMessage("Digite o número do cliente para abrir o chat:")
            .setView(input)
            .setPositiveButton("Abrir") { _, _ ->
                val phone = input.text?.toString()?.trim()?.replace(Regex("[^0-9]"), "") ?: ""
                if (phone.length >= 10) {
                    val conv = PriorityConversation(
                        phone = phone,
                        phoneFormatted = phone,
                        whatsappUrl = "",
                        waitTime = 0,
                        timestamp = System.currentTimeMillis(),
                        lastMessage = 0
                    )
                    if (!conversations.any { it.phone == phone }) {
                        conversations.add(0, conv)
                        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
                    }
                    emptyStateView.visibility = View.GONE
                    selectConversationFromOrder(conv)
                } else {
                    Toast.makeText(requireContext(), "Digite um número válido (mín. 10 dígitos)", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun goBackToConversationsList() {
        selectedConversation = null
        chatContainer.visibility = View.GONE
        
        // Mostrar título "Atendimento" quando voltar para lista
        titleAtendimento.visibility = View.VISIBLE
        
        // Limpar seleção na lista de conversas
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.selectedPhone = null
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
    }
    
    private fun selectConversation(conversation: PriorityConversation) {
        selectedConversation = conversation
        chatContainer.visibility = View.VISIBLE
        
        // Esconder título "Atendimento" quando uma conversa está selecionada
        titleAtendimento.visibility = View.GONE
        
        // Atualizar header do chat com telefone formatado (com código do país)
        val headerPhone = view?.findViewById<TextView>(R.id.chat_header_phone)
        headerPhone?.text = formatPhoneWithCountryCode(conversation.phoneFormatted)
        
        // Mostrar botão WhatsApp
        whatsappButton.visibility = View.VISIBLE
        
        messages.clear()
        (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyDataSetChanged()
        loadMessagesForConversation(conversation.phone)
        
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.selectedPhone = conversation.phone
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
    }
    
    private fun loadMessagesForConversation(phone: String) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val list = withContext(Dispatchers.IO) { apiService.getInboxMessages(phone) }
                messages.clear()
                list.forEach { m ->
                    messages.add(ChatMessage(
                        id = m.id,
                        text = m.body,
                        isAttendant = m.direction == "out",
                        timestamp = try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(m.createdAt) ?: Date() } catch (_: Exception) { Date() }
                    ))
                }
                (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyDataSetChanged()
                scrollToBottom()
            } catch (e: Exception) {
                android.util.Log.e("SupportFragment", "Erro ao carregar mensagens", e)
            }
        }
    }
    
    /** Abre o chat quando o lojista clica em Atendimento no pedido (ele abre para o cliente). */
    private fun selectConversationFromOrder(conversation: PriorityConversation) {
        selectedConversation = conversation
        chatContainer.visibility = View.VISIBLE
        emptyStateView.visibility = View.GONE
        titleAtendimento.visibility = View.GONE
        
        val headerPhone = view?.findViewById<TextView>(R.id.chat_header_phone)
        headerPhone?.text = formatPhoneWithCountryCode(conversation.phoneFormatted)
        
        whatsappButton.visibility = View.VISIBLE
        
        messages.clear()
        (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyDataSetChanged()
        loadMessagesForConversation(conversation.phone)
        
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.selectedPhone = conversation.phone
        (conversationsRecyclerView.adapter as? ConversationsAdapter)?.notifyDataSetChanged()
    }
    
    private fun openWhatsApp() {
        val conversation = selectedConversation ?: return
        
        try {
            val whatsappUrl = conversation.whatsappUrl
            if (whatsappUrl.isNotEmpty()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(whatsappUrl))
                startActivity(intent)
            } else {
                // Fallback: construir URL manualmente
                var phone = conversation.phone.replace(Regex("[^0-9]"), "")
                if (!phone.startsWith("55") && phone.length >= 10) {
                    phone = "55$phone"
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$phone"))
                startActivity(intent)
            }
        } catch (e: Exception) {
            android.util.Log.e("SupportFragment", "Erro ao abrir WhatsApp", e)
            Toast.makeText(requireContext(), "Erro ao abrir WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendMessage() {
        val text = messageInput.text.toString().trim()
        val conversation = selectedConversation
        
        if (text.isEmpty() || conversation == null) return
        
        messageInput.setText("")
        sendButton.isEnabled = false
        
        val tempId = "temp-${System.currentTimeMillis()}"
        val newMessage = ChatMessage(
            id = tempId,
            text = text,
            isAttendant = true,
            timestamp = Date(),
            status = ChatMessageStatus.SENDING
        )
        
        messages.add(newMessage)
        (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyItemInserted(messages.size - 1)
        scrollToBottom()
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Formatar telefone
                var phone = conversation.phone.replace(Regex("[^0-9]"), "")
                if (!phone.startsWith("55") && phone.length >= 10) {
                    phone = "55$phone"
                }
                
                val success = withContext(Dispatchers.IO) {
                    apiService.sendWhatsAppMessage(phone, text)
                }
                
                val messageIndex = messages.indexOfFirst { it.id == tempId }
                if (messageIndex >= 0) {
                    messages[messageIndex] = messages[messageIndex].copy(
                        status = if (success) ChatMessageStatus.SENT else ChatMessageStatus.ERROR
                    )
                    (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyItemChanged(messageIndex)
                }
                
                if (success) {
                    loadMessagesForConversation(conversation.phone)
                } else {
                    Toast.makeText(requireContext(), "Erro ao enviar mensagem", Toast.LENGTH_SHORT).show()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("SupportFragment", "Erro ao enviar mensagem", e)
                val messageIndex = messages.indexOfFirst { it.id == tempId }
                if (messageIndex >= 0) {
                    messages[messageIndex] = messages[messageIndex].copy(status = ChatMessageStatus.ERROR)
                    (messagesRecyclerView.adapter as? MessagesAdapter)?.notifyItemChanged(messageIndex)
                }
                Toast.makeText(requireContext(), "Erro: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                sendButton.isEnabled = true
            }
        }
    }
    
    private fun scrollToBottom() {
        messagesRecyclerView.post {
            if (messages.isNotEmpty()) {
                messagesRecyclerView.smoothScrollToPosition(messages.size - 1)
            }
        }
    }
    
    private fun startAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                loadConversations()
                refreshHandler.postDelayed(this, 5000) // Atualiza a cada 5s para novas conversas aparecerem
            }
        }
        refreshHandler.postDelayed(refreshRunnable!!, 5000)
    }
    
    private fun stopAutoRefresh() {
        refreshRunnable?.let {
            refreshHandler.removeCallbacks(it)
        }
    }
    
    /** Normaliza telefone para comparação (só dígitos, com 55 se 11 dígitos). */
    private fun normalizePhoneForCompare(phone: String): String {
        var digits = phone.replace(Regex("[^0-9]"), "")
        if (digits.length == 11 && !digits.startsWith("55")) digits = "55$digits"
        return digits
    }

    private fun formatPhoneWithCountryCode(phoneFormatted: String): String {
        // Extrair apenas dígitos
        var digits = phoneFormatted.replace(Regex("[^0-9]"), "")
        
        // Se o número não começa com 55 (código do país), adicionar
        if (!digits.startsWith("55") && digits.length >= 10) {
            digits = "55$digits"
        }
        
        // Formatar: +55 (XX) XXXXX-XXXX
        return when {
            digits.length >= 13 -> {
                // Número completo com código do país
                val countryCode = digits.substring(0, 2) // 55
                val ddd = digits.substring(2, 4) // DDD
                val part1 = digits.substring(4, 9) // Primeiros 5 dígitos
                val part2 = digits.substring(9, 13) // Últimos 4 dígitos
                "+$countryCode ($ddd) $part1-$part2"
            }
            digits.length >= 11 -> {
                // DDD + número (sem código do país)
                val ddd = digits.substring(0, 2)
                val part1 = digits.substring(2, 7)
                val part2 = digits.substring(7, 11)
                "+55 ($ddd) $part1-$part2"
            }
            else -> {
                // Fallback: se já está formatado, adicionar +55 no início
                if (phoneFormatted.matches(Regex("\\(\\d{2}\\) \\d{5}-\\d{4}"))) {
                    "+55 $phoneFormatted"
                } else {
                    phoneFormatted
                }
            }
        }
    }
}

data class ChatMessage(
    val id: String,
    val text: String,
    val isAttendant: Boolean,
    val timestamp: Date,
    val status: ChatMessageStatus = ChatMessageStatus.SENT
)

enum class ChatMessageStatus {
    SENDING,
    SENT,
    ERROR
}
