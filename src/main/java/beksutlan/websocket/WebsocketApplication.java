package beksutlan.websocket;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import jakarta.persistence.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@SpringBootApplication
public class WebsocketApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebsocketApplication.class, args);
    }

}

@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {
    private final MyWebSocketHandler myWebSocketHandler;
    private final MyChatHandler myChatHandler;

    WebSocketConfig(MyWebSocketHandler myWebSocketHandler,
                    MyChatHandler myChatHandler) {
        this.myWebSocketHandler = myWebSocketHandler;
        this.myChatHandler = myChatHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(myWebSocketHandler, "/websocket").setAllowedOrigins("*");
        registry.addHandler(myChatHandler, "/chat").setAllowedOrigins("*");
    }
}

record Message (Long from, Long to, String message) {}
record UserResponse(User user, boolean online) {}
@Component
class MyChatHandler implements WebSocketHandler {

    private LinkedHashMap<Long, WebSocketSession> sessions = new LinkedHashMap<>();
    private final UserRepo userRepo;

    private final Gson gson = new Gson();
    MyChatHandler(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // if user exists
        Optional.ofNullable(session.getHandshakeHeaders().getFirst("userId")).ifPresent(stringId -> {
            Long id = Long.valueOf(stringId);
            List<User> users = userRepo.findAll(id);
            List<UserResponse> userResponses = users.stream().map(user -> {
                // check is user online
                return new UserResponse(user, sessions.containsKey(user.getId()));
            }).toList();
            try {
                session.sendMessage(new TextMessage(gson.toJson(userResponses)));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            sessions.put(id, session);
        });
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {

    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}

@RestController
@RequestMapping("/api/users")
class UserController {
    private final UserRepo userRepo;

    UserController(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @GetMapping
    List<User> findAllUsers(@RequestParam(required = false, defaultValue = "0") Long ignore) {
        return userRepo.findAll(ignore);
    }

    @PostMapping
    User save(@RequestBody User user) {
        return userRepo.save(user);
    }
}

@Slf4j
@Component
class MyWebSocketHandler implements WebSocketHandler {

    private final MyMessageRepo myMessageRepo;
    private final UserRepo userRepo;

    private static final Gson gson = new Gson();
    MyWebSocketHandler(MyMessageRepo myMessageRepo,
                       UserRepo userRepo) {
        this.myMessageRepo = myMessageRepo;
        this.userRepo = userRepo;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws IOException {
        // find which user connected
        String stringUserId = session.getHandshakeHeaders().getFirst("userId");
        if (stringUserId != null) {
            Long userId = Long.valueOf(stringUserId);
            User user = userRepo.findById(userId)
                    .orElseThrow(IllegalStateException::new);

            session.sendMessage(new TextMessage(gson.toJson(user.getMyMessages())));
        } else {
            session.sendMessage(new TextMessage("Hello"));
        }

        // if user exists then retrieve MyMessages and send it
        // if not then create message and sent
    }
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        var payload = (String) message.getPayload();
        MyMessage myMessage = new ObjectMapper().readValue(payload, MyMessage.class);
        log.info("Received from '{}' message '{}'", myMessage.getName(), myMessage.getMessage());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {

    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {

    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}

interface MyMessageRepo extends JpaRepository<MyMessage, String> {}
interface UserRepo extends JpaRepository<User, Long> {
    @Query("select u from User u where u.id <> ?1")
    List<User> findAll(Long id);
}
@Entity
@Table(name = "users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonIgnore
    private List<MyMessage> myMessages;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<MyMessage> getMyMessages() {
        return myMessages;
    }

    public void setMyMessages(List<MyMessage> myMessages) {
        this.myMessages = myMessages;
    }
}

@Entity
class MyMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String name;
    private String message;

    @ManyToOne
    private User from;

    @ManyToOne
    private User to;

    public MyMessage() {
    }

    public MyMessage(String name, String message) {
        this.name = name;
        this.message = message;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
