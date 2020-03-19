package kr.khs.maskinfo

import android.graphics.Color
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

//object로 retrofit과 service를 선언하여 어디서든 사용할 수 있도록 한다.
object RetrofitData {
    val BASE_URL = "https://8oi9s0nnth.apigw.ntruss.com/corona19-masks/v1/"
    var retrofit = Retrofit.Builder().baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create()).build()
    var service = retrofit.create(RetrofitNetwork::class.java)
}

//get으로 받을 데이터와 형식이 똑같아야 한다.
data class StoreInfo(val addr : String,
                     val code : String,
                     val name : String,
                     val type : String,
                     val lat : Double,
                     val lng : Double,
                     val stock_at : String,
                     val remain_stat : String,
                     val created_at : String)

data class GetStores(val count : Int,
                     val stores : Array<StoreInfo>)

fun markerColor(stock : String?) =
    when(stock) {
        "break" -> Color.BLACK
        "empty" -> Color.GRAY
        "few" -> Color.RED
        "some" -> Color.YELLOW
        "plenty" -> Color.GREEN
        else -> Color.BLACK
    }

//2020/03/13 09:02:00
fun parseTime(str : String) = "${str.substring(0, 10)} ${str.substring(11, 13)}시 ${str.substring(14, 16)}분 기준"

fun parseStock(stock : String) =
    when(stock) {
        "break" -> "판매 중지"
        "empty" -> "1개 이하"
        "few" -> "2개 이상 30개 미만"
        "some" -> "30개 이상 100개 미만"
        "plenty" -> "100개 이상"
        else -> "produced by KHS"
    }
