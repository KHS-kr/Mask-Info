package kr.khs.maskinfo

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.naver.maps.geometry.GeoConstants
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.*
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.util.FusedLocationSource
import com.naver.maps.map.util.MarkerIcons
import kotlinx.android.synthetic.main.activity_main.*
import kr.khs.maskinfo.RetrofitData.service
import org.jetbrains.anko.alert
import org.jetbrains.anko.longToast
import org.jetbrains.anko.sdk27.coroutines.onEditorAction
import org.jetbrains.anko.toast
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException


class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var locationSource: FusedLocationSource
    companion object {
        private val LOCATION_PERMISSION_REQUEST_CODE = 1000
        private const val GPS_ENABLE_REQUEST_CODE = 2001
        private const val PERMISSIONS_REQUEST_CODE = 100
    }
    private var myLat = 0.0
    private var myLng = 0.0
    private lateinit var map : NaverMap
    val markerList = mutableListOf<Marker>()
    private val searchMarker = Marker().also {
            it.width = 70
            it.height = 100
            it.icon = MarkerIcons.BLACK
            it.iconTintColor = Color.BLUE
            it.captionText = "검색한 위치"
            it.captionColor = Color.GREEN
            it.captionHaloColor = Color.RED
        }

    private var distances = arrayOf("500m", "1km", "3km", "5km")
    private var distance = 1000

    var REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private lateinit var geocoder : Geocoder

    private lateinit var imm : InputMethodManager

    override fun onStart() {
        super.onStart()

        if(!checkLocationServicesStatus())
            showDialogForLocationServiceSetting()
        else
            checkRunTimePermission()

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

        val fm = supportFragmentManager
        val mapFragment = fm.findFragmentById(R.id.map_fragment) as MapFragment?
            ?: MapFragment.newInstance().also {
                fm.beginTransaction().add(R.id.map_fragment, it).commit()
            }

        meter_spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, distances)
        meter_spinner.setSelection(1)
        meter_spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                distance = when(position) {
                    0 -> 500
                    1 -> 1000
                    2 -> 3000
                    3 -> 5000
                    else -> 1000
                }
            }

        }

        geocoder = Geocoder(this)

        locationSource = FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE)

        mapFragment.getMapAsync { naverMap ->
            map = naverMap
//            map.moveCamera(CameraUpdate.scrollTo(LatLng(37.4662, 126.6685)))
            map.locationSource = locationSource
            map.locationTrackingMode = LocationTrackingMode.Follow
            //현재 위치 버튼
            now_location.map = map

            findSale.setOnClickListener {
                findMask(myLat, myLng)
                map.moveCamera(CameraUpdate.scrollTo(LatLng(myLat, myLng)))
            }

            map.addOnLocationChangeListener { location ->
                myLat = location.latitude
                myLng = location.longitude
            }
        }

        location_search_et.onEditorAction { v, actionId, event ->
            if(actionId == EditorInfo.IME_ACTION_SEARCH) {
                locationSearch()
            }
        }

        location_search_btn.setOnClickListener {
            locationSearch()
            imm.hideSoftInputFromWindow(location_search_et.windowToken, 0)
        }

        findSale_search.setOnClickListener {
            findMask(searchMarker.position.latitude, searchMarker.position.longitude)
        }

        KHS.setOnClickListener {
            alert(message="문의 사항 : ks96ks@ajou.ac.kr\n" +
                    "위 정보는 공공데이터 포털의 데이터를 이용한 것입니다.") {
                positiveButton("확인") {  }
            }.show()
        }
    }

    fun locationSearch() {
        var list : List<Address>? = null
        val location = location_search_et.text.toString()
        try {
            list = geocoder.getFromLocationName(location, 1)
        }
        catch(e : IOException) {
            e.printStackTrace()
            toast("주소를 자세히 입력해주세요.")
        }

        if(list == null)
            longToast("해당 주소를 검색하지 못하였습니다.\n" +
                    "주소를 자세히 입력해주세요.")
        else {
            map.moveCamera(CameraUpdate.scrollTo(LatLng(list[0].latitude, list[0].longitude)))
            searchMarker.map = null
            searchMarker.position = LatLng(list[0].latitude, list[0].longitude)
            searchMarker.map = map
            findSale_search.visibility = View.VISIBLE
        }
    }

    fun findMask(lat : Double, lng : Double) {
        markerEraser()
        service.getStores(lat, lng, distance).enqueue(object : Callback<GetStores> {
            override fun onResponse(call: Call<GetStores>, response: Response<GetStores>) { //통신 성공
                if(response.isSuccessful) { //성공적으로 데이터를 받아왔을 경우
                    val getInfo = response.body()!!
                    runOnUiThread {
                        for(store in getInfo.stores) {
                            if(store.remain_stat == "break")
                                continue
                            val marker = Marker().also {
                                it.width = 70
                                it.height = 100
                                it.icon = MarkerIcons.BLACK
                                it.iconTintColor = markerColor(store.remain_stat)
                                it.position = LatLng(store.lat, store.lng)
                                it.captionText = store.name
                                it.captionColor = Color.BLUE
                                it.captionHaloColor = Color.rgb(200, 255, 200)
                                it.setOnClickListener {
                                    alert(title=store.name, message="주소 : ${store.addr}\n" +
                                            "재고 : ${parseStock(store.remain_stat)}\n" +
                                            "마지막 입고 시간 : ${parseTime(store.stock_at)}\n" +
                                            "데이터 업데이트 시간 : ${parseTime(store.created_at)}") {
                                        positiveButton("확인") {  }
                                    }.show()
                                    return@setOnClickListener true
                                }
                                it.map = map
                            }
                            markerList.add(marker)
                        }
                    }
                }
                else //404 등 다른 에러 띄웠을 경우
                    Log.v("TAG", "RESPONSE - ${response.code()} ${response.message()}")
            }
            override fun onFailure(call: Call<GetStores>, t: Throwable) { //통신 실패
                t.printStackTrace(); Log.v("TAG", "RESPONSE - FAIL") }
        })
    }

    fun markerEraser() {
        if(markerList.isNotEmpty()) {
            for(m in markerList)
                m.map = null
            while(markerList.isNotEmpty())
                markerList.removeAt(0)
        }
    }

    override fun onMapReady(naverMap: NaverMap) {
        //작동이안되넹..
//        naverMap.moveCamera(CameraUpdate.scrollTo(LatLng(37.4662, 126.6685)))
//        //네이버에서 제공하는 location
//        naverMap.locationSource = locationSource
//        naverMap.locationTrackingMode = LocationTrackingMode.Follow
//
//        //현재 위치 버튼
//        now_location.map = naverMap
//        now_location.setOnClickListener { toast("kk") }
//
//        naverMap.addOnLocationChangeListener { location ->
//            Toast.makeText(this, "${location.latitude}, ${location.longitude}",
//                Toast.LENGTH_SHORT).show()
//        }
    }

    /*
     * ActivityCompat.requestPermissions를 사용한 퍼미션 요청의 결과를 리턴받는 메소드입니다.
     */
    override fun onRequestPermissionsResult(permsRequestCode: Int, permissions: Array<String?>, grandResults: IntArray) {
        if (locationSource.onRequestPermissionsResult(permsRequestCode, permissions,
                grandResults)) {
            return
        }
        else if (permsRequestCode == PERMISSIONS_REQUEST_CODE && grandResults.size == REQUIRED_PERMISSIONS.size) { // 요청 코드가 PERMISSIONS_REQUEST_CODE 이고, 요청한 퍼미션 개수만큼 수신되었다면
            var check_result = true
            // 모든 퍼미션을 허용했는지 체크합니다.
            for (result in grandResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    check_result = false
                    break
                }
            }
            if (check_result) { //위치 값을 가져올 수 있음
            } else { // 거부한 퍼미션이 있다면 앱을 사용할 수 없는 이유를 설명해주고 앱을 종료합니다.2 가지 경우가 있습니다.
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        REQUIRED_PERMISSIONS.get(0)
                    )
                    || ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        REQUIRED_PERMISSIONS.get(1)
                    )
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "퍼미션이 거부되었습니다. 앱을 다시 실행하여 퍼미션을 허용해주세요.",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "퍼미션이 거부되었습니다. 설정(앱 정보)에서 퍼미션을 허용해야 합니다. ",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            GPS_ENABLE_REQUEST_CODE ->  //사용자가 GPS 활성 시켰는지 검사
                if (checkLocationServicesStatus()) {
                    if (checkLocationServicesStatus()) {
                        Log.d("TAG", "onActivityResult : GPS 활성화 되있음")
                        checkRunTimePermission()
                        return
                    }
                }
        }
    }

    fun checkRunTimePermission() { //런타임 퍼미션 처리
// 1. 위치 퍼미션을 가지고 있는지 체크합니다.
        val hasFineLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val hasCoarseLocationPermission = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (hasFineLocationPermission == PackageManager.PERMISSION_GRANTED &&
            hasCoarseLocationPermission == PackageManager.PERMISSION_GRANTED
        ) { // 2. 이미 퍼미션을 가지고 있다면
// ( 안드로이드 6.0 이하 버전은 런타임 퍼미션이 필요없기 때문에 이미 허용된 걸로 인식합니다.)
// 3.  위치 값을 가져올 수 있음
        } else { //2. 퍼미션 요청을 허용한 적이 없다면 퍼미션 요청이 필요합니다. 2가지 경우(3-1, 4-1)가 있습니다.
// 3-1. 사용자가 퍼미션 거부를 한 적이 있는 경우에는
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this@MainActivity,
                    REQUIRED_PERMISSIONS.get(0)
                )
            ) { // 3-2. 요청을 진행하기 전에 사용자가에게 퍼미션이 필요한 이유를 설명해줄 필요가 있습니다.
                Toast.makeText(this@MainActivity, "이 앱을 실행하려면 위치 접근 권한이 필요합니다.", Toast.LENGTH_LONG)
                    .show()
                // 3-3. 사용자게에 퍼미션 요청을 합니다. 요청 결과는 onRequestPermissionResult에서 수신됩니다.
                ActivityCompat.requestPermissions(
                    this@MainActivity, REQUIRED_PERMISSIONS,
                    PERMISSIONS_REQUEST_CODE
                )
            } else { // 4-1. 사용자가 퍼미션 거부를 한 적이 없는 경우에는 퍼미션 요청을 바로 합니다.
// 요청 결과는 onRequestPermissionResult에서 수신됩니다.
                ActivityCompat.requestPermissions(
                    this@MainActivity, REQUIRED_PERMISSIONS,
                    PERMISSIONS_REQUEST_CODE
                )
            }
        }
    }

    //여기부터는 GPS 활성화를 위한 메소드들
    private fun showDialogForLocationServiceSetting() {
        alert(title = "위치 서비스 활성화", message = "이 앱을 원활히 사용하기 위해선 위치 서비스를 활성화해야합니다.") {
            positiveButton("설정") {
                val callGPSSettingIntent =
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivityForResult(callGPSSettingIntent, GPS_ENABLE_REQUEST_CODE)
            }
            negativeButton("취소") {
                DialogInterface.OnClickListener { dialog, id ->
                    dialog.cancel()
                }
            }
        }
    }

    fun checkLocationServicesStatus(): Boolean {
        val locationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }
}