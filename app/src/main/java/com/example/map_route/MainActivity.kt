package com.example.map_route

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.Debug.getLocation
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request



class MainActivity : AppCompatActivity() {

    lateinit var  mapFragment: SupportMapFragment
    lateinit var googleMap: GoogleMap
    var permissionsToRequest = mutableListOf<String>()
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var latitude: TextView
    private lateinit var longitude: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()
        getLocation()
        mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(OnMapReadyCallback {
            googleMap =it
            if(!hasLocationForgroundPermission()){
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }

            if(permissionsToRequest.isNotEmpty())
            {
                ActivityCompat.requestPermissions(this,permissionsToRequest.toTypedArray(),0)
            }

            val location1 = LatLng(26.85,80.94)
            googleMap.addMarker(MarkerOptions().position(location1).title("My Location"))
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location1,5f))

            val location2 = LatLng(38.89,-77.03)
            googleMap.addMarker(MarkerOptions().position(location2).title("Banglore"))
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location2,5f))

            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
            val URL= getDirectionURL(location1,location2)
            GetDirection(URL).execute()
        })
    }

    private fun hasLocationForgroundPermission()=
        ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_COARSE_LOCATION)== PackageManager.PERMISSION_GRANTED

    private fun requestPermissions(){

        fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode,permissions, grantResults)
            if(requestCode==0 && grantResults.isNotEmpty()){
                for(i in grantResults.indices){
                    if(grantResults[i]== PackageManager.PERMISSION_GRANTED){
                        Log.d("PermissionsRequest","$permissions[i] granted")
                    }
                }

            }

        }
    }

    fun getDirectionURL(origin:LatLng,dest:LatLng): String {
        return "https://maps.googleapis.com/maps/api/directions/json?origin=${origin}&destination=${dest}&sensor=false&mode=driving"
    }
    inner class GetDirection(val url: String) : AsyncTask<Void, Void, List<List<LatLng>>>() {

        override fun doInBackground(vararg params: Void?): List<List<LatLng>> {
            val client = OkHttpClient()
            val request= Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val data = response.body.toString()
            val result= ArrayList<List<LatLng>>()
            try {

                val respObj = Gson().fromJson(data,GoogleMapDTO::class.java)
                val path = ArrayList<LatLng>()

                for(i in 0 until respObj.routes[0].legs[0].steps.size){
                    val startlatlng=LatLng(respObj.routes[0].legs[0].steps[i].start_location.lat.toDouble()
                        ,respObj.routes[0].legs[0].steps[i].start_location.lng.toDouble())
                    path.add(startlatlng)
                    val endlatlng=LatLng(respObj.routes[0].legs[0].steps[i].end_location.lat.toDouble()
                        ,respObj.routes[0].legs[0].steps[i].end_location.lng.toDouble())
                    path.addAll(decodePolyline(respObj.routes[0].legs[0].steps[i].polyline.points))
                }
                result.add(path)
            }catch (e: java.lang.Exception){
                e.printStackTrace()
            }
            return result
            onPostExecute(result)
        }

        override fun onPostExecute(result: List<List<LatLng>>) {
            val lineoption = PolylineOptions()
            for(i in result.indices){

                lineoption.addAll(result[i])
                lineoption.width(10f)
                lineoption.color(Color.BLUE)
                lineoption.geodesic(true)
            }
            googleMap.addPolyline(lineoption)
        }

    }
    fun decodePolyline(encoded: String): List<LatLng>{
        val poly= ArrayList<LatLng>()
        var index=0
        val len= encoded.length
        var lat=0
        var lng=0

        while(index<len){
            var b: Int
            var shift =0
            var result =0
            do{
                val h=Character.getNumericValue(encoded[index++])
                b=h-63
                result=result or (b and 0x1f shl  shift)
                shift+=5
            }while(b>=0x20)
            val dlat= if(result and 1!=0) (result shr 1).inv() else result shr 1
            lat+=dlat

            shift =0
            result =0
            do{
                val v=Character.getNumericValue(encoded[index++])
                b=v-63
                result=result or (b and 0x1f shl shift)
                shift+=5
            }while(b>=0x20)
            val dlng= if(result and 1!=0)(result shr 1).inv() else result shr 1
            lng+=dlng

            val latLng= LatLng((lat.toDouble()/ 1E5),(lng.toDouble()/1E5))
            poly.add(latLng)
        }
        return poly
    }
}



