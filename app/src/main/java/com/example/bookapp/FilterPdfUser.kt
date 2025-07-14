package com.example.bookapp

import android.widget.Filter

class FilterPdfUser: Filter {
    var filterList: ArrayList<ModelPdf>

    var adapterPdfUser: AdapterPdfUser

    constructor(filterList: ArrayList<ModelPdf>, adapterPdfUser: AdapterPdfUser): super() {
        this.filterList = filterList
        this.adapterPdfUser = adapterPdfUser
    }

    override fun performFiltering(constraint: CharSequence): FilterResults {
        var constraint: CharSequence? = constraint

        val results = FilterResults()
        if (constraint != null && constraint.isNotEmpty()){
            constraint = constraint.toString().uppercase()
            val filterModels = ArrayList<ModelPdf>()
            for (i in filterList.indices){
                if(filterList[i].title.uppercase().contains(constraint)){
                    filterModels.add(filterList[i])
                }
            }
            results.count = filterModels.size
            results.values = filterModels
        }
        else{
            results.count = filterList.size
            results.values = filterList
        }
        return results
    }

    override fun publishResults(constraint: CharSequence, results: FilterResults) {

        adapterPdfUser.pdfArrayList = results.values as ArrayList<ModelPdf>

        adapterPdfUser.notifyDataSetChanged()

    }


}