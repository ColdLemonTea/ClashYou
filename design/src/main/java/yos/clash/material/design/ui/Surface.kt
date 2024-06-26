package yos.clash.material.design.ui

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import yos.clash.material.design.BR

class Surface : BaseObservable() {
    var insets: Insets = Insets.EMPTY
        @Bindable get
        set(value) {
            field = value

            notifyPropertyChanged(BR.insets)
        }
}