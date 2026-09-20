import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// Executar com Java 21 e o classpath de dependências, sem iniciar o Spring ou acessar Oracle.
public class GerarHashBCrypt {
    public static void main(String[] args) {
        var console = System.console();
        if (console == null) {
            throw new IllegalStateException("Execute em um terminal interativo.");
        }
        char[] senha = console.readPassword("Senha para gerar hash BCrypt: ");
        if (senha == null) {
            return;
        }
        try {
            System.out.println(new BCryptPasswordEncoder().encode(new String(senha)));
        } finally {
            Arrays.fill(senha, '\0');
        }
    }
}
