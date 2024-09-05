package vn.momo.map

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vn.momo.map.ui.theme.MapTheme

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        MapsInitializer.initialize(this, MapsInitializer.Renderer.LATEST) {
            println(it.name)
        }
        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(android.Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    // Precise location access granted.
                    setContent {
                        MapTheme {
                            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                                Greeting(
                                    name = "Android",
                                    modifier = Modifier.padding(innerPadding)
                                )
                            }
                        }
                    }
                }
                permissions.getOrDefault(android.Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    // Only approximate location access granted.
                } else -> {
                // No location access granted.
            }
            }
        }
        locationPermissionRequest.launch(arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION))



    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnrememberedMutableState", "CoroutineCreationDuringComposition", "MissingPermission")
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {

    val coroutineScope = rememberCoroutineScope()
    var searchResult by remember { mutableStateOf<LocationSearchResult?>(null) }
    var focusSearchItem by remember { mutableStateOf<Result?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val listState = rememberLazyListState()

    val cameraPositionState = rememberCameraPositionState() {
        position = CameraPosition.fromLatLngZoom(LatLng(10.7797855, 106.696444), 15f)
    }
    val ctx = LocalContext.current
    LaunchedEffect(coroutineScope) {
       try {
           val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(ctx)


           val locationResult = fusedLocationProviderClient.lastLocation
           locationResult.addOnCompleteListener(ctx as MainActivity) { task ->
               if (task.isSuccessful) {
                   // Set the map's camera position to the current location of the device.
                   val lastKnownLocation = task.result
                   val deviceLatLng = LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
                   cameraPositionState.position = CameraPosition.fromLatLngZoom(deviceLatLng, 15f)


                   coroutineScope.launch {
                       Toast.makeText(ctx, "Start call API", Toast.LENGTH_LONG).show()
                       val client = HttpClient(CIO)
                       val response: HttpResponse =
                           client.get("https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=${deviceLatLng.latitude},${deviceLatLng.longitude}&radius=5000&type=gas_station&language=vi-VN&key=AIzaSyC4DWLVoxntv2VzvpqRmSKsO_Q3JK1p5ew")

                       if (response.status.value in 200..299) {

                           val result = Json {
                               ignoreUnknownKeys = true
                           }.decodeFromString<LocationSearchResult>(response.body(),)
                           searchResult = result
                       }
                   }
               } else {
                   task.exception?.printStackTrace()
                   Toast.makeText(ctx, "Start call API", Toast.LENGTH_LONG).show()
               }
           }


       } catch (ex: Exception) {
           ex.printStackTrace()
           Toast.makeText(ctx, "Call api error: "+ ex.message, Toast.LENGTH_LONG).show()
       }
    }


    if (showBottomSheet) {
        ModalBottomSheet(onDismissRequest = {
            showBottomSheet = false
        }, sheetState = sheetState,
            modifier = Modifier
                .height(400.dp)
                .background(Color.Transparent),
            scrimColor = Color.Transparent,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                items(searchResult?.results?.size ?: 0) { index ->
                    val item = searchResult?.results?.get(index)
                    Row (
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .background(
                                if (focusSearchItem == item) Color(0xFF3498db) else Color.White
                            )
                            .clickable {


                                coroutineScope.launch {
                                    focusSearchItem = item
                                    listState.scrollToItem(index)
                                    val location = if (item != null) LatLng(
                                        item?.geometry?.location?.lat ?: 0.0,
                                        item.geometry?.location?.lng ?: 0.0
                                    ) else LatLng(10.7797855, 106.696444)
                                    val cameraUpdate = CameraUpdateFactory.newLatLngZoom(location, 15f)
                                    cameraPositionState.animate(cameraUpdate,1000)


                                }
                            }
                        ,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Image(
                                painter = rememberAsyncImagePainter(item?.icon),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp)
                            )
                        }
                        Column {
                            Text(item?.name ?: "", color = Color.Red)
                            Text(item?.vicinity ?: "")
                        }
                    }

                }
            }
        }
    }
    if (searchResult == null) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState
        ) {
            Marker(
                state = MarkerState(position = LatLng(10.7797855, 106.696444)),
                title = "Singapore",
                snippet = "Marker in Singapore"
            )
        }
    } else {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = true),
            uiSettings = MapUiSettings(myLocationButtonEnabled = true)
        ) {
            searchResult?.results?.forEachIndexed { index, it ->
                Marker(
                    state = MarkerState(
                        position = LatLng(
                            it?.geometry?.location?.lat ?: 0.0,
                            it.geometry?.location?.lng ?: 0.0
                        ),

                    ),

                    title = it.name,
                    snippet = it.vicinity,
                    onClick = { _ ->
                        coroutineScope.launch {
                            focusSearchItem = it
                            sheetState.show()
                            showBottomSheet = true

                            listState.scrollToItem(index)
                        }
                        return@Marker  false
                    },


                )
            }
        }
    }


}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MapTheme {
        Greeting("Android")
    }
}

@Serializable
data class LocationSearchResult(
    @SerialName("html_attributions")
    val htmlAttributions: JsonArray? = null,

    @SerialName("next_page_token")
    val nextPageToken: String? = null,

    val results: List<Result>? = null,
    val status: String? = null,
)

@Serializable
data class Result(

    @SerialName("business_status")
    val businessStatus: String? = null,

    val geometry: Geometry? = null,
    val icon: String? = null,

    @SerialName("icon_background_color")
    val iconBackgroundColor: String? = null,

    @SerialName("icon_mask_base_uri")
    val iconMaskBaseURI: String? = null,

    val name: String? = null,
    val photos: List<Photo>? = null,

    @SerialName("place_id")
    val placeID: String? = null,

    @SerialName("plus_code")
    val plusCode: PlusCode? = null,

    val rating: Double? = null,
    val reference: String? = null,
    val scope: String? = null,
    val types: List<String>? = null,

    @SerialName("user_ratings_total")
    val userRatingsTotal: Long? = null,

    val vicinity: String? = null,

    @SerialName("opening_hours")
    val openingHours: OpeningHours? = null
)


@Serializable
data class Geometry(
    val location: Location? = null,
    val viewport: Viewport? = null
)

@Serializable
data class Location(
    val lat: Double? = null,
    val lng: Double? = null
)

@Serializable
data class Viewport(
    val northeast: Location? = null,
    val southwest: Location? = null
)

@Serializable
data class OpeningHours(
    @SerialName("open_now")
    val openNow: Boolean? = null
)

@Serializable
data class Photo(
    val height: Long? = null,

    @SerialName("html_attributions")
    val htmlAttributions: List<String>? = null,

    @SerialName("photo_reference")
    val photoReference: String? = null,

    val width: Long? = null
)

@Serializable
data class PlusCode(
    @SerialName("compound_code")
    val compoundCode: String? = null,

    @SerialName("global_code")
    val globalCode: String? = null
)