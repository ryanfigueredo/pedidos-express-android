package com.pedidosexpress

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout

/**
 * Componente TopAppBar customizado com design moderno:
 * - Título centralizado com tipografia bold
 * - Background em gradiente sutil (laranja escuro para laranja principal)
 * - Bordas inferiores levemente arredondadas
 * - Suporte para botões de ação opcionais
 * - Suporte para subtítulo opcional (ex: contador de pedidos)
 */
class TopAppBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val titleText: TextView
    private val subtitleText: TextView
    private val startActionButton: ImageButton
    private val endActionButton: ImageButton

    init {
        // Inflar o layout
        LayoutInflater.from(context).inflate(R.layout.top_app_bar, this, true)

        // Inicializar views
        titleText = findViewById(R.id.title_text)
        subtitleText = findViewById(R.id.subtitle_text)
        startActionButton = findViewById(R.id.start_action_button)
        endActionButton = findViewById(R.id.end_action_button)

        // Ler atributos customizados se houver
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.TopAppBar, 0, 0)
            
            try {
                // Título
                val title = typedArray.getString(R.styleable.TopAppBar_title)
                title?.let { setTitle(it) }

                // Subtítulo
                val subtitle = typedArray.getString(R.styleable.TopAppBar_subtitle)
                subtitle?.let { setSubtitle(it) }

                // Botão inicial
                val startActionIcon = typedArray.getResourceId(
                    R.styleable.TopAppBar_startActionIcon,
                    0
                )
                if (startActionIcon != 0) {
                    setStartActionIcon(startActionIcon)
                }

                // Botão final
                val endActionIcon = typedArray.getResourceId(
                    R.styleable.TopAppBar_endActionIcon,
                    0
                )
                if (endActionIcon != 0) {
                    setEndActionIcon(endActionIcon)
                }
            } finally {
                typedArray.recycle()
            }
        }
    }

    /**
     * Define o título do header
     */
    fun setTitle(title: String) {
        titleText.text = title
    }

    /**
     * Obtém o título atual
     */
    fun getTitle(): String = titleText.text.toString()

    /**
     * Define o subtítulo (ex: contador de pedidos "(6)")
     * Se null ou vazio, oculta o subtítulo
     */
    fun setSubtitle(subtitle: String?) {
        if (subtitle.isNullOrBlank()) {
            subtitleText.visibility = View.GONE
        } else {
            subtitleText.text = subtitle
            subtitleText.visibility = View.VISIBLE
        }
    }

    /**
     * Obtém o subtítulo atual
     */
    fun getSubtitle(): String? = if (subtitleText.visibility == View.VISIBLE) {
        subtitleText.text.toString()
    } else {
        null
    }

    /**
     * Define o ícone e listener do botão de ação inicial
     * Se iconRes for 0, oculta o botão
     */
    fun setStartActionIcon(
        iconRes: Int,
        onClickListener: OnClickListener? = null
    ) {
        if (iconRes == 0) {
            startActionButton.visibility = View.GONE
        } else {
            startActionButton.setImageResource(iconRes)
            startActionButton.visibility = View.VISIBLE
            onClickListener?.let {
                startActionButton.setOnClickListener(it)
            }
        }
    }

    /**
     * Remove o botão de ação inicial
     */
    fun removeStartAction() {
        startActionButton.visibility = View.GONE
        startActionButton.setOnClickListener(null)
    }

    /**
     * Define o ícone e listener do botão de ação final
     * Se iconRes for 0, oculta o botão
     */
    fun setEndActionIcon(
        iconRes: Int,
        onClickListener: OnClickListener? = null
    ) {
        if (iconRes == 0) {
            endActionButton.visibility = View.GONE
        } else {
            endActionButton.setImageResource(iconRes)
            endActionButton.visibility = View.VISIBLE
            onClickListener?.let {
                endActionButton.setOnClickListener(it)
            }
        }
    }

    /**
     * Remove o botão de ação final
     */
    fun removeEndAction() {
        endActionButton.visibility = View.GONE
        endActionButton.setOnClickListener(null)
    }

    /**
     * Define ambos os botões de ação de uma vez
     */
    fun setActions(
        startIconRes: Int = 0,
        startClickListener: OnClickListener? = null,
        endIconRes: Int = 0,
        endClickListener: OnClickListener? = null
    ) {
        setStartActionIcon(startIconRes, startClickListener)
        setEndActionIcon(endIconRes, endClickListener)
    }
}
