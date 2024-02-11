package com.example.meteo.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.meteo.utils.Constants
import com.example.meteo.R
import com.example.meteo.databinding.ActivityMainBinding
import com.example.meteo.models.WeatherResponse
import com.example.meteo.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.gson.Gson
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

    // A fused location client variable which is further user to get the user's current location
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    // Creating a variable for viewBinding
    private lateinit var binding : ActivityMainBinding

    // A global variable for the SharedPreferences
    private lateinit var mSharedPreferences: SharedPreferences

    // A global variable for Progress Dialog
    private var mProgressDialog: Dialog? = null

    // A global variable for Current Latitude
    private var mLatitude: Double = 0.0
    // A global variable for Current Longitude
    private var mLongitude: Double = 0.0

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize the Fused location variable
        mFusedLocationClient= LocationServices.getFusedLocationProviderClient(this)

        // Initialize the SharedPreferences variable
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME,Context.MODE_PRIVATE)

        /** Call the UI method to populate the data in the UI which are already
         * stored in sharedPreferences earlier. At first run it will be blank */

        setUpUI()

        if(!isLocationEnabled()){
            Toast.makeText(
                this,
                "Please Turned ON Your Location",
                Toast.LENGTH_SHORT
            ).show()

            // This will redirect you to settings from where you need to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }else{
            Dexter.withContext(this)
                .withPermissions(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            ).withListener(object : MultiplePermissionsListener{
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if(report!!.areAllPermissionsGranted()){
                        requestLocationData()
                    }
                    else if(report.isAnyPermissionPermanentlyDenied){
                        showRationalDialogForPermissions()
                        Toast.makeText(
                            this@MainActivity,
                            "You have denied location permission. PLease enabled it."
                            ,Toast.LENGTH_SHORT
                        ).show()
                    }
                    else {// if it's a simple "deny" from the user
                        Toast.makeText(
                            this@MainActivity,
                            "This permission is required to access your location"
                            ,Toast.LENGTH_SHORT
                        ).show()
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

    /** A function to request the current location. Using the fused location provider client. */
    @SuppressLint("MissingPermission")
    private fun requestLocationData(){
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        /**
         * A location callback object of fused location provider client
         * where we will get the current location details.
         */
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {

                val mLastLocation: Location = locationResult.lastLocation!!

                mLatitude = mLastLocation.latitude
                Log.i("Current Latitude", "$mLatitude")
                mLongitude = mLastLocation.longitude
                Log.i("Current Longitude", "$mLongitude")

                getLocationWeatherDetails(mLatitude, mLongitude)
            }
        }

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, locationCallback,
            Looper.getMainLooper()
        )
    }

    /**
     * Function is used to get the weather details of the current
     * location based on the latitude longitude
     */
    private fun getLocationWeatherDetails(latitude:Double,longitude:Double){

        if(Constants.isNetworkAvailable(this)){

            /**
             * Add the built-in converter factory first. This prevents overriding its
             * behavior but also ensures correct behavior when using converters that consume all types.
             */
            val retrofit : Retrofit = Retrofit.Builder()
                // API base URL.
                .baseUrl(Constants.BASE_URL)
                /** Add converter factory for serialization and deserialization of objects. */
                /** Create an instance using a default {@link Gson} instance for conversion.
                 * Encoding to JSON and decoding from JSON
                 * (when no charset is specified by a header) will use UTF-8.
                 */
                .addConverterFactory(GsonConverterFactory.create())
                /** Create the Retrofit instances. */
                .build()

            /**
             * Here we map the service interface in which we declares the end point and the API type
             *i.e GET, POST and so on along with the request parameter which are required.
             */
            val service : WeatherService = retrofit.create(WeatherService::class.java)

            /** An invocation of a Retrofit method that sends a request to a web-server
             * and returns a response. Here we pass the required param in the service
             */
            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude,longitude, Constants.METRIC_UNT, Constants.APP_ID
            )

            // Used to show the progress dialog
            showCustomProgressDialog()

            // Callback methods are executed using the Retrofit callback executor.
            listCall.enqueue(object :Callback<WeatherResponse>{
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>,
                ) {
                    // Check weather the response is success or not.
                    if(response.isSuccessful){

                        hideProgressDialog()

                        /** The de-serialized response body of a successful response. */
                        val weatherList: WeatherResponse? =response.body()
                        Log.i("Weather Report","${weatherList}")

                        // Here we have converted the model class in to Json String to store it in the SharedPreferences.
                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        // Save the converted string to shared preferences
                        val editor=mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA,weatherResponseJsonString)
                        editor.apply()

                        /** Remove the weather detail object as we will be getting
                         *  the object in form of a string in the setup UI method. */
                        setUpUI()
                    }else{
                        // If the response is not success then we check the response code.
                        val rc = response. code()
                        when (rc) {
                            400 -> Log.e("Error 400", "Bad Connection")
                            404 -> Log.e("Error 404", "Not Found")
                            else -> Log.e("Error", "Generic Error")
                        }
                    }
                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    hideProgressDialog() // Hides the progress dialog
                    Log.e("Errorrrrr",t.message.toString())
                }
            })
        }else{
            Toast.makeText(
                this,
                "No Internet Connection Available",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /** A function used to show the alert dialog when the
     * permissions are denied and need to allow it from settings app info. */
    private fun showRationalDialogForPermissions(){
        AlertDialog.Builder(this)
            .setMessage("It looks like you have turned off permissions. " +
                    "It can be Enabled under application Settings")
            .setPositiveButton("Go to Settings"
            ){ _,_ ->
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

    /** A function which is used to verify that the
     * location or GPS is enable or not of the user's device. */
    private fun isLocationEnabled() : Boolean{

        // This provides access to the system location services.
        val locationManager : LocationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /** Method is used to show the Custom Progress Dialog. */
    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)

        /** Set the screen content from a layout resource.
         * The resource will be inflated, adding all top-level views to the screen.*/
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)

        //Start the dialog and display it on screen.
        mProgressDialog!!.show()
    }

    /** This function is used to dismiss the progress dialog if it is visible to user. */
    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId){
            R.id.action_refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Function is used to set the result in the UI elements. */
    @SuppressLint("SetTextI18n")
    @RequiresApi(Build.VERSION_CODES.N)
    private fun setUpUI() {

        /** Here we get the stored response from SharedPreferences and
         * again convert back to data object to populate the data in the UI.*/
        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA,"")

        if(!weatherResponseJsonString.isNullOrBlank()){

            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            // For loop to get the required data. And all are populated in the UI.
            for (z in weatherList!!.weather.indices) {
                Log.i("NAMEEEEEEEE", weatherList.weather[z].main)

                binding.tvMain.text = weatherList.weather[z].main
                binding.tvMainDescription.text = weatherList.weather[z].description
                binding.tvTemp.text = weatherList.main.temp.toString() +
                        getUnit(application.resources.configuration.locales.toString())
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
    }

    /** Function is used to get the temperature unit value. */
    private fun getUnit(value: String): String {
        Log.i("unitttttt", value)
        var unit = "°C"
        if (value == "US" || "LR" == value || "MM" == value) {
            unit = "°F"
        }
        return unit
    }

    /** The function is used to get the formatted time based on the Format and the LOCALE we pass to it. */
    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat")
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}