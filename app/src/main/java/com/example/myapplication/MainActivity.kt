package com.example.myapplication

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), TextWatcher {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        edit.addTextChangedListener(this)
        //edit.bringToFront()
        //scrollView.sendToBack()
        val list = ArrayList<String>()
        for (i in 0..100){
            list.add("")
        }
        val adapter = Adapter(this,list)
        val manager = LinearLayoutManager(this)
        recyclerView.layoutManager=manager
        recyclerView.adapter = adapter
        adapter.notifyDataSetChanged()
        scrollView.setAnimTime(300)
        scrollView.setDamping(0.6f)
        scrollView.setHeaderAdapter(MyHeader(this,250))
        scrollView.setFooterAdapter(MyFooter(this,250))
        scrollView.sendToBack()
    }

    override fun afterTextChanged(s: Editable?) {

    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        Log.e("SL","s=$s\tstart=$start\tcount=$count\tbefore=$before")

    }
    class Adapter(val context: Context,val list:ArrayList<String>) : RecyclerView.Adapter<Adapter.VH>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context).inflate(android.R.layout.activity_list_item,parent,false)
            return VH(view)
        }

        override fun getItemCount(): Int {
            return list.size
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.textView.text="我是谁呢"
        }

        class VH(view:View) : RecyclerView.ViewHolder(view) {
            val textView = view.findViewById<TextView>(android.R.id.text1)
        }
    }
}
