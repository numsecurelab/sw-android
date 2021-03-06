package io.horizontalsystems.atomicswap

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.horizontalsystems.bitcoincore.models.TransactionAddress
import io.horizontalsystems.bitcoincore.models.TransactionInfo
import io.horizontalsystems.dashkit.models.DashTransactionInfo
import java.text.DateFormat
import java.util.*

class TransactionsFragment : Fragment() {

    private lateinit var viewModel: MainViewModel
    private lateinit var transactionsRecyclerView: RecyclerView
    private val transactionsAdapter = TransactionsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activity?.let {
            viewModel = ViewModelProviders.of(it).get(MainViewModel::class.java)

            val transactionsLiveData = when (arguments?.getString("coin")) {
                "BTC" -> viewModel.transactionsBtc
                "BCH" -> viewModel.transactionsBch
                else -> null
            }

            transactionsLiveData?.observe(this, Observer {
                it?.let { transactions ->
                    transactionsAdapter.items = transactions
                    transactionsAdapter.notifyDataSetChanged()
                }
            })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_transactions, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        transactionsRecyclerView = view.findViewById(R.id.transactions)
        transactionsRecyclerView.adapter = transactionsAdapter
        transactionsRecyclerView.layoutManager = LinearLayoutManager(context)
    }
}

class TransactionsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var items = listOf<TransactionInfo>()

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            ViewHolderTransaction(LayoutInflater.from(parent.context).inflate(R.layout.view_holder_transaction, parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ViewHolderTransaction -> holder.bind(items[position], itemCount - position)
        }
    }
}

class ViewHolderTransaction(val containerView: View) : RecyclerView.ViewHolder(containerView) {
    private val summary = containerView.findViewById<TextView>(R.id.summary)!!

    @SuppressLint("SetTextI18n")
    fun bind(transactionInfo: TransactionInfo, index: Int) {
        containerView.setBackgroundColor(if (index % 2 == 0) Color.parseColor("#dddddd") else Color.TRANSPARENT)

        val timeInMillis = transactionInfo.timestamp.times(1000)

        val date = DateFormat.getInstance().format(Date(timeInMillis))
        val amount = NumberFormatHelper.cryptoAmountFormat.format(transactionInfo.amount / 100_000_000.0)

        var text = "#$index"
        if (transactionInfo is DashTransactionInfo) {
            text += "\nInstant: ${transactionInfo.instantTx.toString().toUpperCase()}"
        }

        text += "\nFrom: ${mapAddresses(transactionInfo.from)}" +
                "\nTo: ${mapAddresses(transactionInfo.to)}" +
                "\nAmount: $amount" +
                "\nTx hash: ${transactionInfo.transactionHash}" +
                "\nTx index: ${transactionInfo.transactionIndex}" +
                "\nBlock: ${transactionInfo.blockHeight}" +
                "\nTimestamp: ${transactionInfo.timestamp}" +
                "\nDate: $date"
        summary.text = text
        summary.setOnClickListener {
            println(transactionInfo)
            println(text)
        }
    }

    private fun mapAddresses(list: List<TransactionAddress>): String {
        return list.joinToString("\n ") {
            if (it.mine) {
                "${it.address} (mine)"
            } else {
                it.address
            }
        }
    }
}
