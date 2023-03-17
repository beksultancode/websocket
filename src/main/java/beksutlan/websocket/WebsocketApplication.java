package beksutlan.websocket;

import com.google.gson.Gson;
import jakarta.persistence.*;
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
import java.util.Map;
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
    private final MyChatHandler myChatHandler;

    WebSocketConfig(MyChatHandler myChatHandler) {
        this.myChatHandler = myChatHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(myChatHandler, "/chat").setAllowedOrigins("*");
    }
}

class Message {
    private Long to;
    private String message;

    public Message() {
    }

    public Message(Long to, String message) {
        this.to = to;
        this.message = message;
    }

    public Long getTo() {
        return to;
    }

    public void setTo(Long to) {
        this.to = to;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
record UserResponse(User user, boolean online) {}
@Component
class MyChatHandler implements WebSocketHandler {

    private LinkedHashMap<Long, WebSocketSession> sessions = new LinkedHashMap<>();
    private final UserRepo userRepo;

    private final MessageRepo messageRepo;
    private final Gson gson = new Gson();
    MyChatHandler(UserRepo userRepo,
                  MessageRepo messageRepo) {
        this.userRepo = userRepo;
        this.messageRepo = messageRepo;
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

            sessions.forEach((sId, s) -> {
                try {
                    s.sendMessage(new TextMessage(sId + " is online"));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            sessions.put(id, session);
        });
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String payload = (String) message.getPayload();
        System.out.println("payload = " + payload);
        Message msg = gson.fromJson(payload, Message.class);
        User to = userRepo.findById(msg.getTo()).orElseThrow(EntityNotFoundException::new);

        // check is user online?
        if (sessions.containsKey(to.getId())) {
            WebSocketSession toSession = sessions.get(to.getId());
            toSession.sendMessage(new TextMessage(gson.toJson(msg)));
        }

        // find current user id
        Long currentUserId = 0L;
        for (Map.Entry<Long, WebSocketSession> e : sessions.entrySet()) {
            if (e.getValue().getId().equals(session.getId())) {
                currentUserId = e.getKey();
            }
        }

        // save message to database
        messageRepo.save(new MessageEntity(null, msg.getMessage(), currentUserId, msg.getTo()));
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

interface UserRepo extends JpaRepository<User, Long> {
    @Query("select u from User u where u.id <> ?1")
    List<User> findAll(Long id);
}

interface MessageRepo extends JpaRepository<MessageEntity, Long> {}
@Entity
@Table(name = "users")
class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

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
}


@Entity
@Table(name = "messages")
class MessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String message;
    private Long fromUser;
    private Long toUser;

    public MessageEntity() {
    }

    public MessageEntity(Long id, String message, Long fromUser, Long toUser) {
        this.id = id;
        this.message = message;
        this.fromUser = fromUser;
        this.toUser = toUser;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getFromUser() {
        return fromUser;
    }

    public void setFromUser(Long fromUser) {
        this.fromUser = fromUser;
    }

    public Long getToUser() {
        return toUser;
    }

    public void setToUser(Long toUser) {
        this.toUser = toUser;
    }
}
