package mx.gob.jovenes.guanajuato.api;

import java.util.ArrayList;

import mx.gob.jovenes.guanajuato.model.Tweet;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Query;

/**
 * Created by codigus on 26/05/2017.
 */

public interface TwitterAPI {

    @Headers("Authorization:OAuth oauth_consumer_key=\"zHb326FK2xT666c0olifieF9h\",oauth_token=\"865314952470560768-BZ5AXIzHyRndtxWqcYe8u2rKMIb25cF\",oauth_signature_method=\"HMAC-SHA1\",oauth_timestamp=\"1496184096\",oauth_nonce=\"01k3D4\",oauth_version=\"1.0\",oauth_signature=\"gb3WY4uc21j37mIBiwSpu68cQhk%3D\"")
    @GET("user_timeline.json")

    Call<ArrayList<Tweet>> get (
            @Query("screen_name") String screenName
    );

}
