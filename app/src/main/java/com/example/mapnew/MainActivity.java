package com.example.mapnew;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.esri.arcgisruntime.ArcGISRuntimeEnvironment;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.loadable.LoadStatus;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.LocationDisplay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.security.AuthenticationManager;
import com.esri.arcgisruntime.security.DefaultAuthenticationChallengeHandler;
import com.esri.arcgisruntime.security.OAuthConfiguration;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleMarkerSymbol;
import com.esri.arcgisruntime.symbology.TextSymbol;
import com.esri.arcgisruntime.tasks.geocode.GeocodeParameters;
import com.esri.arcgisruntime.tasks.geocode.GeocodeResult;
import com.esri.arcgisruntime.tasks.geocode.LocatorTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Route;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteParameters;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteResult;
import com.esri.arcgisruntime.tasks.networkanalysis.RouteTask;
import com.esri.arcgisruntime.tasks.networkanalysis.Stop;
import com.esri.arcgisruntime.util.ListenableList;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;


public class MainActivity extends AppCompatActivity {
    private TextView textTime, textDistance;
    private MapView mMapView = null;
    private LocationDisplay mLocationDisplay = null;
    private SearchView mSearchView = null;
    private GraphicsOverlay mGraphicsOverlay = null;
    private LocatorTask mLocatorTask = null;
    private GeocodeParameters mGeocodeParameters = null;
    private Point mStart, mEnd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mSearchView = findViewById(R.id.locationSearcher);
        mMapView = findViewById(R.id.mapView);
        textTime = findViewById(R.id.labelTime);
        textDistance = findViewById(R.id.labelDistance);

        setupLocator();
        setupMap();
        setupLocationDisplay();
        setupOAuthManager();

        findDecoder();
    }

    //Устанавливает локатор для ArcGis, аналогично Address
    private void setupLocator() {
        //Вызывает адрес службы локализации
        String locatorService = getResources().getString(R.string.geocode_url);
        mLocatorTask = new LocatorTask(locatorService);
        //Прослушивает службу с заданными параметрами
        mLocatorTask.addDoneLoadingListener(() -> {
            if (mLocatorTask.getLoadStatus() == LoadStatus.LOADED) {
                mGeocodeParameters = new GeocodeParameters();
                mGeocodeParameters.getResultAttributeNames().add("*");
                //Максимальное количество результатов поиска
                mGeocodeParameters.setMaxResults(1);

                mGraphicsOverlay = new GraphicsOverlay();
                mMapView.getGraphicsOverlays().add(mGraphicsOverlay);
            } else if (mSearchView != null) {
                mSearchView.setEnabled(false);
            }
        });
        mLocatorTask.loadAsync();
    }

    //Ищет точку по запросу
    private void findDecoder() {
        //Запускает прослушивание поисковой строки
        mSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            //Активирует событие, если текст был введен в поисковую строку
            @Override
            public boolean onQueryTextSubmit(String s) {
                String location = mSearchView.getQuery().toString();
                if(location != null || !location.equals("")) {
                    try {
                        mLocatorTask.cancelLoad();

                        //Получает список всех возможных запросов
                        final ListenableFuture<List<GeocodeResult>> geocodeFuture = mLocatorTask.geocodeAsync(location, mGeocodeParameters);
                        List<GeocodeResult> geocodeResults = geocodeFuture.get();

                        //Задает координаты начала(наше расположение) и конца(точки адреса запроса)
                        mStart = new Point(mMapView.getLocationDisplay().getLocation().getPosition().getX(), mMapView.getLocationDisplay().getLocation().getPosition().getY());
                        mEnd = new Point(geocodeResults.get(0).getRouteLocation().getX(), geocodeResults.get(0).getRouteLocation().getY());

                        //Запускает поиск и построение маршрута
                        findRoute();


                        //Ставит маркер по самому первому результату поиска
                        displaySearchResult(geocodeResults.get(0));

                    } catch (InterruptedException | ExecutionException e) {
                        showError("Адрес не найден: " + e.getMessage());
                    }

                }
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                return false;
            }
        });
    }

    /*
    private void setMapMarker(Point location, SimpleMarkerSymbol.Style style, int markerColor, int outlineColor) {
        float markerSize = 8.0f;
        float markerOutlineThickness = 2.0f;
        SimpleMarkerSymbol pointSymbol = new SimpleMarkerSymbol(style, markerColor, markerSize);
        pointSymbol.setOutline(new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, outlineColor, markerOutlineThickness));
        Graphic pointGraphic = new Graphic(location, pointSymbol);
        mGraphicsOverlay.getGraphics().add(pointGraphic);
    }

    private void setStartMarker(Point location) {
        mGraphicsOverlay.getGraphics().clear();
        setMapMarker(location, SimpleMarkerSymbol.Style.DIAMOND, Color.rgb(226, 119, 40), Color.BLUE);
        mStart = location;
        mEnd = null;
    }

    private void setEndMarker(Point location) {
        setMapMarker(location, SimpleMarkerSymbol.Style.SQUARE, Color.rgb(40, 119, 226), Color.RED);
        mEnd = location;
        findRoute();
    }



    private void mapClicked(Point location) {
        if (mStart == null) {
            // Start is not set, set it to a tapped location
            setStartMarker(location);
        } else if (mEnd == null) {
            // End is not set, set it to the tapped location then find the route
            setEndMarker(location);
        } else {
            // Both locations are set; re-set the start to the tapped location
            setStartMarker(location);
        }
    }
    */






    //Ищет маршрут
    private void findRoute() {
        //Вызывает адрес службы маршрутизации
        String routeServiceURI = getResources().getString(R.string.routing_url);
        final RouteTask solveRouteTask = new RouteTask(getApplicationContext(), routeServiceURI);
        solveRouteTask.loadAsync();

        //Прослушивает службу с заданными параметрами
        solveRouteTask.addDoneLoadingListener(() -> {
            if (solveRouteTask.getLoadStatus() == LoadStatus.LOADED) {
                final ListenableFuture<RouteParameters> routeParamsFuture = solveRouteTask.createDefaultParametersAsync();
                routeParamsFuture.addDoneListener(() -> {
                    try {
                        RouteParameters routeParameters = routeParamsFuture.get();
                        List<Stop> stops = new ArrayList<>();
                        stops.add(new Stop(mStart));
                        stops.add(new Stop(mEnd));
                        routeParameters.setStops(stops);

                        // Задает первый возвращенный маршрут
                        final ListenableFuture<RouteResult> routeResultFuture = solveRouteTask.solveRouteAsync(routeParameters);
                        routeResultFuture.addDoneListener(() -> {
                            try {
                                RouteResult routeResult = routeResultFuture.get();
                                Route firstRoute = routeResult.getRoutes().get(0);
                                // Рисует маршрут
                                Polyline routePolyline = firstRoute.getRouteGeometry();
                                SimpleLineSymbol routeSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 2.0f);
                                Graphic routeGraphic = new Graphic(routePolyline, routeSymbol);
                                mGraphicsOverlay.getGraphics().add(routeGraphic);

                                //Отображение дистанций
                                long distanceInKm = Math.round(firstRoute.getTotalLength() / 1000);
                                long timeInMinutes = Math.round(firstRoute.getTravelTime());

                                textTime.setText("Время: " + timeInMinutes + " мин.");
                                textDistance.setText("Расстояние: " + distanceInKm + " км.");

                                //Toast.makeText(getApplicationContext(), "Расстояние (Км.): " + distanceInKm + " Время (Мин.): " + timeInMinutes, Toast.LENGTH_LONG).show();
                            } catch (InterruptedException | ExecutionException e) {
                                showError("Не удалось построить маршрут: " + e.getMessage());
                            }
                        });
                    } catch (InterruptedException | ExecutionException e) {
                        showError("Не удалось задать параметры RouteTask: " + e.getMessage());
                    }
                });
            } else {
                showError("Не удалось загрузить RouteTask: " + solveRouteTask.getLoadStatus().toString());
            }
        });
    }

    //Ставит маркер по координатам запроса
    private void displaySearchResult(GeocodeResult geocodedLocation) {
        String displayLabel = geocodedLocation.getLabel();
        TextSymbol textLabel = new TextSymbol(10, displayLabel, Color.GREEN, TextSymbol.HorizontalAlignment.CENTER, TextSymbol.VerticalAlignment.BOTTOM);
        Graphic textGraphic = new Graphic(geocodedLocation.getDisplayLocation(), textLabel);
        Graphic mapMarker = new Graphic(geocodedLocation.getDisplayLocation(), geocodedLocation.getAttributes(),
                new SimpleMarkerSymbol(SimpleMarkerSymbol.Style.CIRCLE, Color.MAGENTA, 12.0f));
        ListenableList allGraphics = mGraphicsOverlay.getGraphics();
        allGraphics.clear();
        allGraphics.add(mapMarker);
        allGraphics.add(textGraphic);
        mMapView.setViewpointCenterAsync(geocodedLocation.getDisplayLocation());
    }

    // Отображает локацию устройства
    private void setupLocationDisplay() {
        mLocationDisplay = mMapView.getLocationDisplay();


        //Запускает прослушивание
        mLocationDisplay.addDataSourceStatusChangedListener(dataSourceStatusChangedEvent -> {
            if (dataSourceStatusChangedEvent.isStarted() || dataSourceStatusChangedEvent.getError() == null) {
                return;
            }
            int requestPermissionsCode = 2;
            String[] requestPermissions = new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

            //Проверяет наличие прав на определение местоположения устройства, если же его нет, то запрашивает его. Иначе выдает ошибку
            if (!(ContextCompat.checkSelfPermission(MainActivity.this, requestPermissions[0]) == PackageManager.PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(MainActivity.this, requestPermissions[1]) == PackageManager.PERMISSION_GRANTED)) {
                ActivityCompat.requestPermissions(MainActivity.this, requestPermissions, requestPermissionsCode);
            } else {
                String message = String.format("Ошибка. Разрешите получать ваше местоположение: %s",
                        dataSourceStatusChangedEvent.getSource().getLocationDataSource().getError().getMessage());
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });

        // Поведение MapView при получении обновления местоположения

        mLocationDisplay.setAutoPanMode(LocationDisplay.AutoPanMode.RECENTER);
        mLocationDisplay.startAsync();
    }




    //Авторизирует пользователя по его ID
    private void setupOAuthManager() {
        String clientId = getResources().getString(R.string.client_id);
        String redirectUrl = getResources().getString(R.string.redirect_url);

        try {
            OAuthConfiguration oAuthConfiguration = new OAuthConfiguration("https://www.arcgis.com", clientId, redirectUrl);
            DefaultAuthenticationChallengeHandler authenticationChallengeHandler = new DefaultAuthenticationChallengeHandler(this);
            AuthenticationManager.setAuthenticationChallengeHandler(authenticationChallengeHandler);
            AuthenticationManager.addOAuthConfiguration(oAuthConfiguration);
        } catch (MalformedURLException e) {
            showError(e.getMessage());
        }
    }
    private void showError(String message) {
        Log.d("FindRoute", message);
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mLocationDisplay.startAsync();
        } else {
            Toast.makeText(MainActivity.this, getResources().getString(R.string.location_permission_denied), Toast.LENGTH_SHORT).show();
        }
    }

    //Устанавливает карту на полотно
    private void setupMap() {
        if (mMapView != null) {
            Basemap.Type basemapType = Basemap.Type.STREETS_NIGHT_VECTOR;
            int levelOfDetail = 13;
            ArcGISMap map = new ArcGISMap(basemapType, 0, 0, levelOfDetail);
            mMapView.setMap(map);
            ArcGISRuntimeEnvironment.setLicense(getResources().getString(R.string.arcgis_license_key));
        }
    }

    @Override
    protected void onPause() {
        if (mMapView != null) {
            mMapView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mMapView != null) {
            mMapView.resume();
        }
    }

    @Override
    protected void onDestroy() {
        if (mMapView != null) {
            mMapView.dispose();
        }
        super.onDestroy();
    }

}