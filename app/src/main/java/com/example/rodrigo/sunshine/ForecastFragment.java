package com.example.rodrigo.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ArrayAdapter<String> mForecastAdapter;

    public ForecastFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mForecastAdapter = new ArrayAdapter<String>(
                getActivity(), //Contexto atual
                R.layout.list_item_forecast, //ID do List Item Layout
                R.id.list_item_forecast_textview, //ID do List View pra popular
                new ArrayList<String>() //Dados
        );

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        //Pega a referência do List View e anexa ao Adapter
        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long l) {
                String forecast = mForecastAdapter.getItem(position);
                Toast.makeText(getActivity(), forecast, Toast.LENGTH_SHORT).show();;
                Intent intent = new Intent(getActivity(), DetailActivity.class)
                        .putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });

        return rootView;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if(id == R.id.action_refresh) {
            updateWeather();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void updateWeather() {
        FetchWeatherTask weatherTask = new FetchWeatherTask();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String location = prefs.getString(getString(R.string.pref_location_key),
                getString(R.string.pref_location_default));
        weatherTask.execute(location);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        @Override
        protected String[] doInBackground(String... params) {
            // Necessário declarar fora do try/catch
            // assim podem ser fechados no bloco finally.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Armazena os dados JSON como uma string.
            String forecastJsonStr = null;

            String format = "json";
            String units = "metric";
            int numDays = 7;
            String appid = "b7dad7730000b103e638993dc4f2615f";

            try {

                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String DAYS_PARAM = "cnt";
                final String APPID = "APPID";

                Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, params[0])
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                        .appendQueryParameter(APPID, appid)
                        .build();

                URL url = new URL(builtUri.toString());

                // Cria uma requisição ao OpenWeatherMap, e abre uma conexão
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Lê o input stream para uma String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if(inputStream == null) return null;

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while((line = reader.readLine()) != null) {
                    // Desde que seja JSON, adiciona uma nova linha se necessário (não afeta o parsing)
                    // Mas deixa o debugginf muito mais fácil se for exibido por completo
                    // buffer para debugging.
                    buffer.append(line + "\n");
                }

                if(buffer.length() == 0) return null;
                forecastJsonStr = buffer.toString();
            }catch(IOException e) {
                Log.e(LOG_TAG, "Erro ", e);
                // Se o código não obtiver sucesso ao obter os dados, não é necessário fazer o parse.
                return null;
            }finally {
                if(urlConnection != null) {
                    urlConnection.disconnect();
                }

                if(reader != null) {
                    try {
                        reader.close();
                    }catch(final IOException e) {
                        Log.e(LOG_TAG, "Erro fechando stream", e);
                    }
                }
            }

            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            }catch(JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            if(result != null) {
                mForecastAdapter.clear();
                for(String dayForecastStr : result) {
                    mForecastAdapter.add(dayForecastStr);
                }
            }
        }

        // A conversão de data/hora fica fora do asynctask
        private String getReadableDateString(long time) {
            // Como a API retorna um timestamp unix (medido em segundos),
            // tem que ser convertido em milissegundos para ser convertido em uma data válida.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        // Prepara os dados de tempo alto/baixo para exibição
        private String formatHighLows(double high, double low, String unitType) {
            if(unitType.equals(getString(R.string.pref_units_imperial))) {
                high = (high * 1.8) + 32;
                low = (low * 1.8) + 32;
            }else if(!unitType.equals(getString(R.string.pref_units_metric))) {
                Log.d(LOG_TAG, "Tipo de unidade de medida não encontrado: " + unitType);
            }
            // Para exibição não é necessário se importar com os décimos de graus.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        /**
         * String de previsão completa no formato JSON com os dados necessários para construir
         * os wireframes.
         *
         * O parsing é fácil: O constructor pega a string de JSON e converte em uma hierarquia
         * de objetos.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException {
            // Os nomes de objetos JSON que precisam ser extraidos.
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            // Retorna as previsões diárias com base na hora local da cidade que está sendo
            // pesquisada, sendo assim necessário saber o deslocamento GMT para traduzir esses
            // dados devidamente.

            // Uma vez que os dados também são enviados em ordem e o primeiro dia sempre é o dia atual,
            // vamos aproveitar para fazer uma boa data UTC normalizada para todo nosso tempo.

            Time dayTime = new Time();
            dayTime.setToNow();

            // nós iniciamos no dia retornado pela hora local. Senão isso vira uma bagunça.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];

            // Os dados são recuperados em Celsius por padrão.
            // Se o usuário preferir ver em Fahrenheit, converte o valor aqui.
            // Nós não pegamos os dados em Fahrenheit, assim o usuário pode
            // mudar as opções sem a necessidade de recuperar os dados novamente
            // uma vez que temos os dados armazenados no banco de dados.
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(
                    getString(R.string.pref_units_key),
                    getString(R.string.pref_units_metric)
            );

            for(int i = 0; i < weatherArray.length(); i++) {
                // Por agora, usamos o formato "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Pega o objeto JSON que representa o dia
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // A data/hora é retornada como um long. Precisamos converter isso em algo
                // legível por humanos, já qe a maioria das pessoas não entende "1400356800"
                // como sendo um "sábado".
                long dateTime;

                // Código para converter a hora UTC
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);

                // Descrição está num array chamado "tempo", sendo um elemento long.
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                // Temperatura está em um objeto chamado "temp", Tente não nomear varáveis com o
                // nome "temp" quando trabalhar com temperatura. Isto confunde todo mundo.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low, unitType);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }

            return resultStrs;

        }
    }
}
