package mx.gob.admic.fragments;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.w3c.dom.Text;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import io.realm.Realm;
import mx.gob.admic.R;
import mx.gob.admic.adapters.RVDocumentoEventoAdapter;
import mx.gob.admic.api.EventoAPI;
import mx.gob.admic.api.Response;
import mx.gob.admic.application.MyApplication;
import mx.gob.admic.model.Evento;
import mx.gob.admic.model.EventoResponse;
import mx.gob.admic.sesion.Sesion;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;

/**
 * Created by uriel on 21/06/16.
 */
public class DetalleEventoFragment extends Fragment implements OnMapReadyCallback {
    private static String ID_EVENTO = "id_evento";
    private Evento evento;
    private MapFragment mapaEvento;
    private TextView tvNombreEvento;
    private TextView textViewAreaResponsable;
    private TextView tvDireccionEvento;
    private TextView tvDescripcionEvento;
    private TextView tvFechaEvento;
    private RecyclerView rvDocumentosEvento;
    private RVDocumentoEventoAdapter adapter;
    private TextView textViewNoHayDocumentos;

    private Button botonEstoyEnEvento;
    public static Button botonMeInteresa;
    private TextView textViewEventoCaducado;
    private TextView textViewYaHasSidoRegistrado;
    private Realm realm;
    private LocationManager locationManager;
    private static final int PERMISSION_REQUEST_CODE = 321;
    private double latitud;
    private double longitud;
    private ProgressDialog progressDialog;
    private Retrofit retrofit;
    private EventoAPI eventoAPI;
    private static final String ERROR_YA_REGISTRADO = "Ya has sido registrado";
    private static final String ERROR_FUERA_DE_RANGO = "No te encuentras en el rango del evento";

    private Criteria criteria;

    public static DetalleEventoFragment newInstance(int idEvento) {
        DetalleEventoFragment detalleEventoFragment = new DetalleEventoFragment();
        Bundle args = new Bundle();
        args.putInt(ID_EVENTO, idEvento);//cambia el valor de la variable por el id de la region seleccionada
        detalleEventoFragment.setArguments(args);
        return detalleEventoFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        realm = MyApplication.getRealmInstance();
        locationManager = (LocationManager) getActivity().getSystemService(Context.LOCATION_SERVICE);
        retrofit = ((MyApplication) getActivity().getApplication()).getRetrofitInstance();
        eventoAPI = retrofit.create(EventoAPI.class);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_detalle_evento, container, false);

        evento = realm.where(Evento.class).equalTo("idEvento", getArguments().getInt(ID_EVENTO)).findFirst();

        criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);

        progressDialog = new ProgressDialog(getContext());

        mapaEvento = (MapFragment) getActivity().getFragmentManager().findFragmentById(R.id.mapa_evento);
        mapaEvento.getMapAsync(this);

        tvNombreEvento = (TextView) v.findViewById(R.id.tv_nombre_evento);
        tvDireccionEvento = (TextView) v.findViewById(R.id.tv_direccion_evento);
        tvDescripcionEvento = (TextView) v.findViewById(R.id.tv_descripcion_evento);
        tvFechaEvento = (TextView) v.findViewById(R.id.tv_fechas_evento);
        botonEstoyEnEvento = (Button) v.findViewById(R.id.boton_estoy_en_el_evento);
        botonMeInteresa = (Button) v.findViewById(R.id.boton_me_interesa);
        textViewEventoCaducado = (TextView) v.findViewById(R.id.textview_evento_caducado);
        textViewYaHasSidoRegistrado = (TextView) v.findViewById(R.id.textview_registrado);
        textViewAreaResponsable = (TextView) v.findViewById(R.id.textview_area_responsable);
        rvDocumentosEvento = (RecyclerView) v.findViewById(R.id.rv_documentos_evento);
        textViewNoHayDocumentos = (TextView) v.findViewById(R.id.tv_empty_documentos);

        adapter = new RVDocumentoEventoAdapter(getContext(), evento.getDocumentosEvento());

        rvDocumentosEvento.setAdapter(adapter);
        rvDocumentosEvento.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        if (evento.getDocumentosEvento().size() == 0) {
            rvDocumentosEvento.setVisibility(View.GONE);
            textViewNoHayDocumentos.setVisibility(View.VISIBLE);
        }

        tvNombreEvento.setText(evento.getTitulo());

        if (evento.getArea() != null) {
            textViewAreaResponsable.setText(evento.getArea().getNombre());
        } else {
            textViewAreaResponsable.setText("Este evento no tiene un área responsable");
        }

        tvDireccionEvento.setText(evento.getDireccion());
        tvDescripcionEvento.setText(evento.getDescripcion());
        tvFechaEvento.setText(getFechaCast(evento.getFechaInicio()) + " - " + getFechaCast(evento.getFechaFin()));

        if (!usuarioRegistrado()) checkAsist();

        botonEstoyEnEvento.setOnClickListener((View) -> {
            progressDialog = ProgressDialog.show(getContext(), "Cargando", "Obteniendo tu localización", true, true);

            Call<Response<EventoResponse>> call = eventoAPI.marcarEvento(evento.getIdEvento(), Sesion.getUsuario().getApiToken(), getLatitud(), getLongitud());

            call.enqueue(new Callback<Response<EventoResponse>>() {
                @Override
                public void onResponse(Call<Response<EventoResponse>> call, retrofit2.Response<Response<EventoResponse>> response) {

                    if (response.body() != null) {
                        if (response.body().errors.length == 0) {
                            int puntos = response.body().data.getPuntosOtorgados();
                            int puntosUsuario = Integer.parseInt(Sesion.getUsuario().getPuntaje());
                            String puntosFinal = String.valueOf(puntos + puntosUsuario);
                            Sesion.getUsuario().setPuntaje(puntosFinal);

                            Snackbar.make(getView(), "Registrado!", Snackbar.LENGTH_LONG).show();

                            realm.beginTransaction();
                            evento.setEstaRegistrado(true);
                            realm.commitTransaction();

                        } else if (response.body().errors[0].equals(ERROR_FUERA_DE_RANGO)) {
                            Snackbar.make(getView(), ERROR_FUERA_DE_RANGO, Snackbar.LENGTH_LONG).show();
                        } else if (response.body().errors[0].equals(ERROR_YA_REGISTRADO)) {
                            Snackbar.make(getView(), ERROR_YA_REGISTRADO, Snackbar.LENGTH_LONG).show();

                            realm.beginTransaction();
                            evento.setEstaRegistrado(true);
                            realm.commitTransaction();
                        }
                    } else {
                        Snackbar.make(getView(), "Ops! parece que ocurrio un error, intenta más tarde", Snackbar.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Response<EventoResponse>> call, Throwable t) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setMessage("Error de conexión");
                    builder.show();
                }
            });

        });

        botonMeInteresa.setOnClickListener((View) -> {
            //Genera la llamada para poder enviar un correo
            ProgressDialog progressDialog = new ProgressDialog(getContext());
            progressDialog.setTitle("Enviando correo");
            progressDialog.setMessage("Espera mientras enviamos un correo a tu cuenta registrada");
            progressDialog.show();

            Call<Response<Boolean>> enviarCorreo = eventoAPI.enviarCorreo(Sesion.getUsuario().getApiToken(), evento.getIdEvento());

            enviarCorreo.enqueue(new Callback<Response<Boolean>>() {
                @Override
                public void onResponse(Call<Response<Boolean>> call, retrofit2.Response<Response<Boolean>> response) {
                    Snackbar.make(getView(), "Fallo en enviar o ya se encuentra inscrito", 7000).show();
                    progressDialog.dismiss();
                }

                @Override
                public void onFailure(Call<Response<Boolean>> call, Throwable t) {
                    Snackbar.make(getView(), "Gracias por estar interesado en el evento, en breve te llegará un correo electrónico con más información.", 7000).show();
                    MyApplication.contadorCorreosEventos.start();
                    progressDialog.dismiss();
                }
            });

        });

        return v;
    }

    public void checkAsist() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateInStringbegin = evento.getFechaInicio();
        String dateInStringend = evento.getFechaFin();
        String dateInStringToday = formatter.format(new Date());

        try {
            Date fechainicio = formatter.parse(dateInStringbegin);
            Date fechafin = formatter.parse(dateInStringend);
            Date today = formatter.parse(dateInStringToday);

            long timeStampBegin = fechainicio.getTime();
            long timeStampEnd = fechafin.getTime();
            long timeStampToday = today.getTime();

            boolean antesDeFecha = timeStampBegin > timeStampToday;
            boolean enFecha = timeStampBegin < timeStampToday && timeStampToday < timeStampEnd;
            boolean despuesDeFecha = timeStampEnd < timeStampToday;

            System.err.println(antesDeFecha);
            System.err.println(timeStampBegin < timeStampToday);
            System.err.println(despuesDeFecha);


            if (antesDeFecha) {
                botonMeInteresa.setVisibility(View.VISIBLE);
            } else if (despuesDeFecha) {
                textViewEventoCaducado.setVisibility(View.VISIBLE);
            } else if (enFecha) {
                botonEstoyEnEvento.setVisibility(View.VISIBLE);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pedirPermisos();
                } else {
                    try {
                        locationManager.requestSingleUpdate(criteria, new LocationListener() {
                            @Override
                            public void onLocationChanged(Location location) {
                                setLatitud(location.getLatitude());
                                setLongitud(location.getLongitude());
                            }

                            @Override
                            public void onStatusChanged(String provider, int status, Bundle extras) {

                            }

                            @Override
                            public void onProviderEnabled(String provider) {

                            }

                            @Override
                            public void onProviderDisabled(String provider) {

                            }
                        }, null);
                        /*SingleShootLocationProvider.requestSingleUpdate(getContext(), new SingleShootLocationProvider.LocationCallBack() {
                            @Override
                            public void onNewLocationAvailable(GPSCoordinates location) {
                                setLatitud(location.getLatitude());
                                setLongitud(location.getLongitude());
                            }
                        });*/
                    } catch (SecurityException e) {
                        e.printStackTrace();
                    }
                }

            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private String getFechaCast(String fecha) {
        SimpleDateFormat formato = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        SimpleDateFormat miFormato = new SimpleDateFormat("dd/MM/yyyy");

        try {
            String reformato = miFormato.format(formato.parse(fecha));
            return reformato;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        float zoomLevel = (float) 16.0;
        LatLng coordenadas = new LatLng(evento.getLatitud(), evento.getLongitud()); //coordenadas de la región
        googleMap.addMarker(new MarkerOptions().position(coordenadas).title(evento.getTitulo())); //pone el puntero en las coordenadas
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coordenadas, zoomLevel)); //hace el zoom en el mapa
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mapaEvento = (MapFragment) getActivity().getFragmentManager().findFragmentById(R.id.mapa_evento);
        if (mapaEvento != null)
            getActivity().getFragmentManager().beginTransaction().remove(mapaEvento).commit();
    }

    private void pedirPermisos() {
        String[] permisos = new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permisos, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean permitir = true;

        switch (requestCode) {
            case PERMISSION_REQUEST_CODE:
                for (int res : grantResults) {
                    permitir = permitir && (res == PackageManager.PERMISSION_GRANTED);
                }
                break;
            default:
                permitir = false;
                break;
        }


        if (permitir) {
            try {
                locationManager.requestSingleUpdate(criteria, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        setLatitud(location.getLatitude());
                        setLongitud(location.getLongitude());
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {

                    }

                    @Override
                    public void onProviderEnabled(String provider) {

                    }

                    @Override
                    public void onProviderDisabled(String provider) {

                    }
                }, null);
                /*SingleShootLocationProvider.requestSingleUpdate(getContext(), location -> {
                    setLatitud(location.getLatitude());
                    setLongitud(location.getLongitude());
                });*/

            } catch (SecurityException e) {
                e.printStackTrace();
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Snackbar.make(getActivity().findViewById(R.id.segunda_fragment_container), "Permiso denegado", Snackbar.LENGTH_LONG).show();
                }
            }
        }
    }

    private void setLatitud(double latitud) {
        this.latitud = latitud;
    }

    private void setLongitud(double longitud) {
        this.longitud = longitud;
    }

    private double getLatitud() {
        return this.latitud;
    }

    private double getLongitud() {
        return this.longitud;
    }

    private boolean usuarioRegistrado() {
        if (evento.getEstaRegistrado()) {
            botonEstoyEnEvento.setVisibility(View.GONE);
            botonMeInteresa.setVisibility(View.GONE);
            textViewEventoCaducado.setVisibility(View.GONE);
            textViewYaHasSidoRegistrado.setVisibility(View.VISIBLE);
            return true;
        }
        return false;
    }

    private static class SingleShootLocationProvider {

        interface LocationCallBack {
             void onNewLocationAvailable(GPSCoordinates location);
        }

        public static void requestSingleUpdate(final Context context, final LocationCallBack callBack) {
            final LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            if (isNetworkEnabled) {
                Criteria criteria = new Criteria();
                criteria.setAccuracy(Criteria.ACCURACY_COARSE);

                locationManager.requestSingleUpdate(criteria, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        callBack.onNewLocationAvailable(new GPSCoordinates(location.getLatitude(), location.getLongitude()));
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {
                    }

                    @Override
                    public void onProviderEnabled(String provider) {
                    }

                    @Override
                    public void onProviderDisabled(String provider) {
                    }
                }, null);
            } else {
                boolean isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

                if (isGPSEnabled) {
                    Criteria criteria = new Criteria();
                    criteria.setAccuracy(Criteria.ACCURACY_COARSE);

                    locationManager.requestSingleUpdate(criteria, new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            callBack.onNewLocationAvailable(new GPSCoordinates(location.getLatitude(), location.getLongitude()));
                        }

                        @Override
                        public void onStatusChanged(String provider, int status, Bundle extras) {}

                        @Override
                        public void onProviderEnabled(String provider) {}

                        @Override
                        public void onProviderDisabled(String provider) {}

                    }, null);
                }
            }

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            }

        }

    }

    private static class GPSCoordinates {
        public float latitude = -1;
        public float longitude = -1;

        public GPSCoordinates(float latitude, float longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public GPSCoordinates(double latitude, double longitude) {
            this.latitude = (float) latitude;
            this.longitude = (float) longitude;
        }

        public float getLatitude() {
            return this.latitude;
        }

        public float getLongitude() {
            return this.longitude;
        }

    }

}