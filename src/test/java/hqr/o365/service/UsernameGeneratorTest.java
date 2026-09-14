package hqr.o365.service;
import static org.junit.jupiter.api.Assertions.*;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
class UsernameGeneratorTest {
 @Test void emptyPrefixAndLettersLength() { UsernameGenerator g=new UsernameGenerator(); String v=g.next("","letters",12,"",0,100); assertTrue(v.matches("[a-z]{12}")); }
 @Test void createsManyUniqueRandomNames() { UsernameGenerator g=new UsernameGenerator(); Set<String>s=new HashSet<String>(); for(int i=0;i<5000;i++)assertTrue(s.add(g.next("","st1",10,"",i,5000))); }
 @Test void sequenceHonorsWidth() { UsernameGenerator g=new UsernameGenerator(); assertEquals("usr0007",g.next("usr","st2",4,"",7,10)); }
 @Test void regexGeneration() { UsernameGenerator g=new UsernameGenerator(); for(int i=0;i<100;i++)assertTrue(g.next("","regex",8,"^[a-z]{6}[0-9]{2}$",i,100).matches("[a-z]{6}[0-9]{2}")); }
 @Test void invalidRegexRejected() { assertThrows(IllegalArgumentException.class,()->new UsernameGenerator().next("","regex",5,"(a|b)+",0,1)); }
}
