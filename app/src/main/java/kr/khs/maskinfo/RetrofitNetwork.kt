package kr.khs.maskinfo

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

//127.0.0.1/khs?api_key={key}의 형태로 값이 들어간다.
interface RetrofitNetwork {
    @GET("storesByGeo/json")
    fun getStores(@Query("lat") lat : Double,
                @Query("lng") lng : Double,
                @Query("m") m : Int = 1000) : Call<GetStores>
}

//{
//    "count": 0,
//    "stores": [
//    {
//        "code": "string",
//        "name": "string",
//        "addr": "string",
//        "type": "string",
//        "lat": 0,
//        "lng": 0,
//        "stock_at": "string",
//        "remain_stat": "string",
//        "created_at": "string"
//    }
//    ]
//}