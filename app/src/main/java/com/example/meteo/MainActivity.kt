package com.example.meteo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.meteo.databinding.ActivityMainBinding
import com.example.meteo.models.WeatherResponse
import com.example.meteo.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var binding : ActivityMainBinding

    private var mProgressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        mFusedLocationClient= LocationServices.getFusedLocationProviderClient(this)

        if(!isLocationEnabled()){
            Toast.makeText(this,"Please Turned ON Your Location",Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if(report!!.areAllPermissionsGranted()){
                        requestLocationData()
                    }
                    else if(report.isAnyPermissionPermanentlyDenied){
                        showRationalDialogForPermissions()
                        Toast.makeText(this@MainActivity,
                            "You have denied location permission. PLease enabled it."
                            ,Toast.LENGTH_SHORT).show()
                    }
                    else {  //if it's a simple "deny" from the user
                        Toast.makeText(this@MainActivity,
                            "This permission is required to access your location"
                            ,Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?,
                ) {
                    token?.continuePermissionRequest()
                }
            }).onSameThread().check()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,mLocationCallback, Looper.myLooper())
    }

    val mLocationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation : Location? = locationResult.lastLocation
            val latitude = mLastLocation!!.latitude
            val longitude = mLastLocation.longitude
            Log.e("Current location","lat:$latitude,long:$longitude")
            getLocationWeatherDetails(latitude,longitude)
        }
    }

    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){
        if(Constants.isNetworkAvailable(this)){
            val retrofit : Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service : WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,longitude,Constants.METRIC_UNT,Constants.APP_ID)
            showCustomProgressDialog()
            listCall.enqueue(object :Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>,
                ) {
                    hideProgressDialog()
                    if(response.isSuccessful){
                        val weatherList: WeatherResponse? =response.body()
                        setUpUI(weatherList)
                        Log.i("Weather Report","${weatherList}")
                    }else{
                        val rc = response. code()
                        when (rc) {
                            400 -> Log.e("Error 400", "Bad Connection")
                            404 -> Log.e("Error 404", "Not Found")
                            else -> Log.e("Error", "Generic Error")
                        }
                    }
                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog()
                    Log.e("Errorrrrr",t.message.toString())
                }
            })

        }else{
            Toast.makeText(this,"No Internet Connection Available",
                Toast.LENGTH_SHORT).show()
        }
    }

    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions. It can be Enabled under application Settings")
            .setPositiveButton("Go to Settings"){
                _,_ ->
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        e.printStackTrace()
                    }
            }
            .setNegativeButton("Cancel"){
                dialog,_ -> dialog.cancel()
            }.show()
    }

    private fun isLocationEnabled() : Boolean{
        val locationManager : LocationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Method is used to show the Custom Progress Dialog.
     */
    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        /*Set the screen content from a layout resource.
        The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }

    /**
     * This function is used to dismiss the progress dialog if it is visible to user.
     */
    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    /**
     * Function is used to set the result in the UI elements.
     */
    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun setUpUI(weatherList: WeatherResponse?) {

        // For loop to get the required data. And all are populated in the UI.
        for (z in weatherList!!.weather.indices) {
            Log.i("NAMEEEEEEEE", weatherList.weather[z].main)

            binding.tvMain.text = weatherList.weather[z].main
            binding.tvMainDescription.text = weatherList.weather[z].description
            binding.tvTemp.text = weatherList.main.temp.toString() + getUnit(application.resources.configuration.locales.toString())
            binding.tvHumidity.text = weatherList.main.humidity.toString() + " per cent"
            binding.tvMin.text = weatherList.main.temp_min.toString() + " min"
            binding.tvMax.text = weatherList.main.temp_max.toString() + " max"
            binding.tvSpeed.text = weatherList.wind.speed.toString()
            binding.tvName.text = weatherList.name
            binding.tvCountry.text = weatherList.sys.country
            binding.tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
            binding.tvSunsetTime.text = unixTime(weatherList.sys.sunset)

            // Here we update the main icon
            when (weatherList.weather[z].icon) {
                "01d" -> binding.ivMain.setImageResource(R.drawable.sunny)
                "02d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04d" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "04n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10d" -> binding.ivMain.setImageResource(R.drawable.rain)
                "11d" -> binding.ivMain.setImageResource(R.drawable.storm)
                "13d" -> binding.ivMain.setImageResource(R.drawable.snowflake)
                "01n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "02n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "03n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "10n" -> binding.ivMain.setImageResource(R.drawable.cloud)
                "11n" -> binding.ivMain.setImageResource(R.drawable.rain)
                "13n" -> binding.ivMain.setImageResource(R.drawable.snowflake)
            }
        }
    }

    /**
     * Function is used to get the temperature unit value.
     */
    private fun getUnit(value: String): String {
        Log.i("unitttttt", value)
        var unit = "°C"
        if (value == "US" || "LR" == value || "MM" == value) {
            unit = "°F"
        }
        return unit
    }

    /**
     * The function is used to get the formatted time based on the Format and the LOCALE we pass to it.
     */
    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat")
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}