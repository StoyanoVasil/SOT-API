package service.endpoint;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.glassfish.jersey.client.ClientConfig;
import service.models.*;

import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Path("/")
public class RentalService {

    private WebTarget client;
    private JWTVerifier verifier;
    private String test;

    public RentalService() {
        this.client = null;
        this.verifier = JWT.require(Algorithm.HMAC256("rest_sot_assignment")).build();
    }

    private void setClient(UriInfo uri) {

        if (this.client == null) {
            String u = getUriBase(uri.getBaseUri().toString());
            URI baseUri = UriBuilder.fromUri(u).build();
            Client client = ClientBuilder.newClient(new ClientConfig());
            this.client = client.target(baseUri);
        }
    }

    private String getUriBase(String uri) {

        String[] parts = uri.split(":");
        String port = parts[2].split("/")[0];
        return parts[0] + ":" + parts[1] + ":" + port + "/";
    }

    private DecodedJWT verifyToken(String token) {

        return verifier.verify(token);
    }

    private String getTokenId(String token) {

        return verifyToken(token).getKeyId();
    }

    private String getLandlord(Room room, String token) {

        Builder req = this.client
                .path("user/api/name/" + room.getLandlord())
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", token);
        Response r = req.get();
        if(r.getStatus() == 200) {
            return r.readEntity(String.class);
        }
        return null;
    }

    private String getTenant(Room room, String token) {

        Builder req = this.client
                .path("user/api/name/" + room.getTenant())
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", token);
        Response r = req.get();
        if(r.getStatus() == 200) {
            return r.readEntity(String.class);
        }
        return null;
    }

    // unprotected routes
    @POST
    @Path("new/user")
    @Produces(MediaType.APPLICATION_JSON)
    public Response register(@FormParam("email") String email, @FormParam("name") String name,
                             @FormParam("role") String role, @FormParam("password") String password,
                             @Context UriInfo uri) {

        setClient(uri);
        if (role.equals("student") || role.equals("landlord")) {
            Builder reqBuilder1 = this.client.path("user/api/register")
                    .request(MediaType.TEXT_PLAIN).accept(MediaType.APPLICATION_JSON);
            return reqBuilder1.post(Entity.entity(new User(email, name, password, role),
                    MediaType.APPLICATION_JSON));
        }
        return Response.status(422).build();
    }

    @POST
    @Path("user/authenticate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticate(@FormParam("email") String email, @FormParam("password") String password,
                                 @Context UriInfo uri) {

        setClient(uri);
        Form form = new Form();
        form.param("email", email);
        form.param("password", password);
        Builder reqBuilder1 = this.client.path("user/api/authenticate").request(MediaType.APPLICATION_JSON);
        return reqBuilder1.post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
    }

    //protected routes
    @GET
    @Path("user/all")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUsers(@HeaderParam("Authorization") String token, @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            Builder reqBuilder1 = this.client
                    .path("user/api/all")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            return reqBuilder1.get();
        } catch (JWTVerificationException e) {
            return Response.status(401).entity(e).build();
        }
    }

    @GET
    @Path("user/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserById(@PathParam("id") String id, @HeaderParam("Authorization") String token,
                                @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            Builder reqBuilder1 = this.client
                    .path("user/api/user/" + id)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            return reqBuilder1.get();
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @DELETE
    @Path("delete/user/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeUser(@PathParam("id") String id, @HeaderParam("Authorization") String token,
                               @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            //get role from user service
            Builder reqBuilder = this.client
                    .path("user/api/role/" + id)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r = reqBuilder.get();

            //if landlord delete all rooms, if user remove all bookings/rents and free rooms
            String role = r.readEntity(String.class);
            if (role.equals("landlord")) {
                Builder req = this.client
                        .path("room/api/rooms/" + id + "/delete")
                        .request(MediaType.APPLICATION_JSON)
                        .header("Authorization", token);
                req.delete();
            } else if (role.equals("student")) {
                Builder req = this.client
                        .path("room/api/rooms/" + id + "/update")
                        .request(MediaType.APPLICATION_JSON)
                        .header("Authorization", token);
                req.get();
            }

            //delete user
            Builder reqBuilder1 = this.client
                    .path("user/api/remove/" + id)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            return reqBuilder1.delete();
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("room/all")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAllRooms(@HeaderParam("Authorization") String token, @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            Builder reqBuilder1 = this.client
                    .path("room/api/all")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r =  reqBuilder1.get();
            if (r.getStatus() == 200) {
                GenericType<ArrayList<Room>> ent = new GenericType<>() {};
                List<Room> rooms = r.readEntity(ent);
                for (Room room : rooms) {
                    String landlord = getLandlord(room, token);
                    String tenant = getTenant(room, token);
                    room.setLandlord(landlord);
                    room.setTenant(tenant);
                }
                return Response.status(200).entity(rooms).type(MediaType.APPLICATION_JSON).build();
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("room/free")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getFreeRooms(@HeaderParam("Authorization") String token, @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            Builder reqBuilder1 = this.client
                    .path("room/api/free")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r = reqBuilder1.get();
            if (r.getStatus() == 200) {
                GenericType<ArrayList<Room>> ent = new GenericType<>() {};
                List<Room> rooms = r.readEntity(ent);
                for (Room room : rooms) {
                    String landlord = getLandlord(room, token);
                    String tenant = getTenant(room, token);
                    room.setLandlord(landlord);
                    room.setTenant(tenant);
                }
                return Response.status(200).entity(rooms).type(MediaType.APPLICATION_JSON).build();
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("room/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoomById(@PathParam("id") String id, @HeaderParam("Authorization") String token,
                                @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            Builder reqBuilder1 = this.client
                    .path("room/api/room/" + id)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            return reqBuilder1.get();
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("room/city")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoomByCity(@QueryParam("city") String city, @HeaderParam("Authorization") String token,
                                  @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            Builder reqBuilder1 = this.client
                    .path("room/api/rooms")
                    .queryParam("city", city)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r = reqBuilder1.get();
            if (r.getStatus() == 200) {
                GenericType<ArrayList<Room>> ent = new GenericType<>() {};
                List<Room> rooms = r.readEntity(ent);
                for (Room room : rooms) {
                    String landlord = getLandlord(room, token);
                    String tenant = getTenant(room, token);
                    room.setLandlord(landlord);
                    room.setTenant(tenant);
                }
                return Response.status(200).entity(rooms).type(MediaType.APPLICATION_JSON).build();
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("room/landlord")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoomsByLandlord(@HeaderParam("Authorization") String token, @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            String id = getTokenId(token);
            Builder req = this.client
                    .path("room/api/rooms/landlord/" + id)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r = req.get();
            if (r.getStatus() == 200) {
                GenericType<ArrayList<Room>> ent = new GenericType<>() {};
                List<Room> rooms = r.readEntity(ent);
                for (Room room : rooms) {
                    String landlord = getLandlord(room, token);
                    String tenant = getTenant(room, token);
                    room.setLandlord(landlord);
                    room.setTenant(tenant);
                }
                return Response.status(200).entity(rooms).type(MediaType.APPLICATION_JSON).build();
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("room/tenant")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getRoomsByTenant(@HeaderParam("Authorization") String token, @Context UriInfo uri) {

        setClient(uri);
        try {
            String id = getTokenId(token);
            Builder req = this.client
                    .path("room/api/rooms/tenant/" + id)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r = req.get();
            if (r.getStatus() == 200) {
                GenericType<ArrayList<Room>> ent = new GenericType<>() {};
                List<Room> rooms = r.readEntity(ent);
                for (Room room : rooms) {
                    String landlord = getLandlord(room, token);
                    String tenant = getTenant(room, token);
                    room.setLandlord(landlord);
                    room.setTenant(tenant);
                }
                return Response.status(200).entity(rooms).type(MediaType.APPLICATION_JSON).build();
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @POST
    @Path("new/room")
    @Produces(MediaType.APPLICATION_JSON)
    public Response newRoom(@FormParam("address") String address,
                            @FormParam("city") String city,
                            @FormParam("rent") int rent,
                            @HeaderParam("Authorization") String token,
                            @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            String id = getTokenId(token);
            Room room = new Room(address, city, id, rent);
            Builder reqBuilder1 = this.client
                    .path("room/api/new")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            return reqBuilder1.post(Entity.entity(room, MediaType.APPLICATION_JSON));
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("book/room/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response bookRoom(@PathParam("id") String id, @HeaderParam("Authorization") String token,
                             @Context UriInfo uri) {

        setClient(uri);
        try {
            DecodedJWT jwt = verifyToken(token);
            Response r = getUserById(jwt.getKeyId(), token, uri);
            if (r.getStatus() == 200) {
                User user = r.readEntity(User.class);
                if (user.getCanBook()) {
                    Builder reqBuilder1 = this.client
                            .path("room/api/room/" + id + "/book")
                            .request(MediaType.APPLICATION_JSON)
                            .header("Authorization", token);
                    r = reqBuilder1.get();
                    if (r.getStatus() == 204) {
                        user.setCanBook(false);
                        Builder req = this.client
                                .path("user/api/user/update")
                                .request(MediaType.APPLICATION_JSON)
                                .header("Authorization", token);
                        req.put(Entity.entity(user, MediaType.APPLICATION_JSON));
                    }
                    return r;
                }
                return Response.status(401).entity("User can't book!").type(MediaType.TEXT_PLAIN).build();
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).entity("User not authorized!").type(MediaType.TEXT_PLAIN).build();
        }
    }

    @GET
    @Path("rent/room/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response rentRoom(@PathParam("id") String id, @HeaderParam("Authorization") String token,
                             @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            Builder reqBuilder1 = this.client
                    .path("room/api/room/" + id + "/rent")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r = reqBuilder1.get();
            if (r.getStatus() == 204) {
                Room room = getRoomById(id, token, uri).readEntity(Room.class);
                User user = getUserById(room.getTenant(), token, uri).readEntity(User.class);
                user.setCanBook(true);
                Builder req = this.client
                        .path("user/api/user/update")
                        .request(MediaType.APPLICATION_JSON)
                        .header("Authorization", token);
                req.put(Entity.entity(user, MediaType.APPLICATION_JSON));
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @DELETE
    @Path("delete/room/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteRoom(@PathParam("id") String id, @HeaderParam("Authorization") String token,
                               @Context UriInfo uri) {

        setClient(uri);
        try {
            verifyToken(token);
            Builder reqBuilder1 = this.client
                    .path("room/api/room/" + id + "/delete")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            return reqBuilder1.delete();
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    @GET
    @Path("cancel/booking/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response cancelBooking(@PathParam("id") String id, @HeaderParam("Authorization") String token,
                                  @Context UriInfo uri) {

        setClient(uri);
        try {
            DecodedJWT jwt = verifyToken(token);
            Builder req = this.client
                    .path("room/api/room/" + id + "/book/cancel")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r = req.get();
            if (r.getStatus() == 204) {
                User user = getUserById(jwt.getKeyId(), token, uri).readEntity(User.class);
                user.setCanBook(true);
                req = this.client
                        .path("user/api/user/update")
                        .request(MediaType.APPLICATION_JSON)
                        .header("Authorization", token);
                req.put(Entity.entity(user, MediaType.APPLICATION_JSON));
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }

    private Response getRoomsByTenantId(String id, String token, UriInfo uri) {

        setClient(uri);
        try {
            Builder req = this.client
                    .path("room/api/rooms/tenant/" + id)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", token);
            Response r = req.get();
            if (r.getStatus() == 200) {
                GenericType<ArrayList<Room>> ent = new GenericType<>() {};
                List<Room> rooms = r.readEntity(ent);
                for (Room room : rooms) {
                    String landlord = getLandlord(room, token);
                    String tenant = getTenant(room, token);
                    room.setLandlord(landlord);
                    room.setTenant(tenant);
                }
                return Response.status(200).entity(rooms).type(MediaType.APPLICATION_JSON).build();
            }
            return r;
        } catch (JWTVerificationException e) {
            return Response.status(401).build();
        }
    }
}
