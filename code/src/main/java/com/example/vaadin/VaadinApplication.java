package com.example.vaadin;

import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.messages.MessageInput;
import com.vaadin.flow.component.messages.MessageList;
import com.vaadin.flow.component.messages.MessageListItem;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.Command;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import jakarta.annotation.security.PermitAll;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;

@SpringBootApplication
@Push
public class VaadinApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(VaadinApplication.class, args);
    }

}

@Route("")
@PermitAll
class ChatView extends VerticalLayout {

    ChatView(ChatService service) {

        var messageList = new MessageList();
        var textInput = new MessageInput();

        setSizeFull();
        add(messageList, textInput);
        expand(messageList);
        textInput.setWidthFull();

        service.join().subscribe(message -> {
            var nl = new ArrayList<>(messageList.getItems());
            nl.add(new MessageListItem(message.text(), message.time(), message.username()));
            getUI().ifPresent(ui -> ui.access((Command) () -> messageList.setItems(nl)));
        });
        textInput.addSubmitListener(event -> service.add(event.getValue()));

    }

}

@Route("login")
class LoginView extends VerticalLayout {

    LoginView() {
        var form = new LoginForm();
        form.setAction("login");
        add(form);
    }
}


@Configuration
class SecurityConfiguration extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        setLoginView(http, LoginView.class);
    }

    @Bean
    UserDetailsManager userDetailsManager() {
        var users = Set.of("marcus", "josh", "tiffany")
                .stream()
                .map(name -> User.withDefaultPasswordEncoder().username(name).password("pw").roles("USER").build())
                .toList();
        return new InMemoryUserDetailsManager(users);
    }

}


record Message(String username, String text, Instant time) {
}

@Service
class ChatService {

    private final Sinks.Many<Message> messages = Sinks.many().multicast().directBestEffort();

    private final Flux<Message> messagesFlux = messages.asFlux();

    private final AuthenticationContext ctx;

    ChatService(AuthenticationContext ctx) {
        this.ctx = ctx;
    }

    Flux<Message> join() {
        return this.messagesFlux;
    }

    void add(String message) {
        var username = this.ctx.getPrincipalName().orElse("Anonymous");
        this.messages.tryEmitNext(new Message(username, message, Instant.now()));
    }

}