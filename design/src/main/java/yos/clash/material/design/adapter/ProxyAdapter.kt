package yos.clash.material.design.adapter

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import yos.clash.material.design.component.ProxyView
import yos.clash.material.design.component.ProxyViewConfig
import yos.clash.material.design.component.ProxyViewState

class ProxyAdapter(
    private val config: ProxyViewConfig,
    private val clicked: (String) -> Unit,
) : RecyclerView.Adapter<ProxyAdapter.Holder>() {
    class Holder(val view: ProxyView) : RecyclerView.ViewHolder(view)

    var selectable: Boolean = false
    var states: List<ProxyViewState> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ProxyView(config.context, config))
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = states[position]

        holder.view.apply {
            state = current

            setOnClickListener {
                clicked(current.proxy.name)
            }

            val isSelector = selectable

            isFocusable = isSelector
            isClickable = isSelector

            current.update(true)
        }
    }

    override fun getItemCount(): Int {
        return states.size
    }
}
