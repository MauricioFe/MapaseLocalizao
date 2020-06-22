package com.example.mapaselocalizao;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.text.Layout;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.concurrent.TimeUnit;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final int REQUEST_ERRO_PLAY_SERVICES = 1;
    private static final String EXTRA_DIALOG = "dialog";
    public static final int REQUEST_CHECAR_GPS = 3;
    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    private GoogleMap mMap;
    LatLng mOrigem;
    GoogleApiClient mGoogleApiClient;
    private FusedLocationProviderClient fusedLocationClient;
    Handler mHandler;
    boolean mDeveExibirDialog;
    int mTentativas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();
        mDeveExibirDialog = savedInstanceState == null;
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        findViewById(R.id.localização_atual_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verificarStatusGPS();
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        super.onSaveInstanceState(outState, outPersistentState);
        outState.putBoolean(EXTRA_DIALOG, true);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mDeveExibirDialog = savedInstanceState.getBoolean(EXTRA_DIALOG, true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected())
            mGoogleApiClient.disconnect();
        mHandler.removeCallbacksAndMessages(null);
        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ERRO_PLAY_SERVICES && resultCode == RESULT_OK)
            mGoogleApiClient.connect();
        else if(requestCode == REQUEST_CHECAR_GPS){
            if (resultCode == Activity.RESULT_OK){
                mTentativas =0;
                mHandler.removeCallbacksAndMessages(null);
                obterUltimaLocalizacao();
            }else{
                Toast.makeText(this, R.string.erro_gps, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mOrigem = new LatLng(-23.561706, -46.655981);
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        mMap.getUiSettings().setMapToolbarEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
        // Add a marker in Sydney and move the camera
        mMap.addMarker(new MarkerOptions()
                .position(mOrigem)
                .title("Avinida Paulista")
                .snippet("São Paulo"));
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(mOrigem)
                .zoom(17)
                .bearing(90)
                .tilt(45)
                .build();
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, REQUEST_ERRO_PLAY_SERVICES);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else
            exibirMensagemDeErro(this, connectionResult.getErrorCode());
    }

    private void exibirMensagemDeErro(FragmentActivity activity, final int errorCode) {
        final String TAG = "DIALOG_ERRO_PLAY_SERVICES";
        if (getSupportFragmentManager().findFragmentByTag(TAG) == null) {
            DialogFragment errorFragment = new DialogFragment() {
                @NonNull
                @Override
                public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
                    return GooglePlayServicesUtil.getErrorDialog(errorCode, getActivity(), REQUEST_ERRO_PLAY_SERVICES);
                }
            };
            errorFragment.show(activity.getSupportFragmentManager(), TAG);
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    private void obterUltimaLocalizacao() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 0);
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        // Got last known location. In some rare situations this can be null.
                        if (location != null) {
                            mTentativas =0;
                            mOrigem = new LatLng(location.getLatitude(), location.getLongitude());
                            atualizaMapa();
                        }else if (mTentativas < 10){
                            mTentativas++;
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    obterUltimaLocalizacao();
                                }
                            }, 2000);
                        }
                    }
                });
    }

    private void atualizaMapa() {
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mOrigem, 17.0f));
        mMap.clear();
        mMap.addMarker(new MarkerOptions().position(mOrigem).title("Localização atual"));
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }
/*Estamos verificando se a configuração de localização está habilitada utilizando o método checkLocationSettings da classe
*  SettingsApi. Esse método retorna um PendingResult, que, como o próprio nome diz, é um resultado pendente que será verificado ao
* invocarmos o método setResultCallBackç. Nesse caso, o método onResult será chamado, e lá verificamos se a configuração de
* localização do aparelho ffoi ativada; nesse caso, iniciamos a detecção da localização GPS. Com o objeto LocationSettingsResult
* verificamos o se o status da configuração; teremos o status SUCCESS, mas se obtivermos o resultado RESOLUTION REQUIRED, devemos
* solicitar ao usuario que habilite essa configuração. Para isso, invocamos o método startResolutionForResult no qual será
* exibido a mensagem*/
    private void verificarStatusGPS() {
        final LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder locationSettingsRequest = new LocationSettingsRequest.Builder();
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, locationSettingsRequest.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                final Status status = locationSettingsResult.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        obterUltimaLocalizacao();
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        if (mDeveExibirDialog) {
                            try {
                                status.startResolutionForResult(MapsActivity.this, REQUEST_CHECAR_GPS);
                                mDeveExibirDialog = false;
                            } catch (IntentSender.SendIntentException e) {
                                e.printStackTrace();
                            }
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        Log.wtf("NGVL", "Isso não deveria acontecer...");
                        break;
                }
            }
        });
    }
}