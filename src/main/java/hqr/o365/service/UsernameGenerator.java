package hqr.o365.service;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Generates unique Microsoft 365 mail nicknames without network access. */
public class UsernameGenerator {
    private static final String ALNUM = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";
    private static final Pattern TOKEN = Pattern.compile("(\\[[^\\]]+\\]|\\\\.|[^\\[\\]{}])(?:\\{(\\d+)(?:,(\\d+))?\\})?");
    private final SecureRandom random;
    private final Set<String> generated = new HashSet<String>();

    public UsernameGenerator() { this(new SecureRandom()); }
    UsernameGenerator(SecureRandom random) { this.random = random; }

    public String next(String prefix, String strategy, int length, String regex, int sequence, int total) {
        String safePrefix = prefix == null ? "" : prefix.trim().toLowerCase();
        int safeLength = length <= 0 ? defaultLength(strategy, total) : length;
        if (safeLength > 48) throw new IllegalArgumentException("随机位数不能超过48");
        for (int attempt=0; attempt<10000; attempt++) {
            String suffix;
            if ("st2".equals(strategy)) suffix = leftPad(sequence, safeLength);
            else if ("letters".equals(strategy)) suffix = randomFrom(LETTERS, safeLength);
            else if ("regex".equals(strategy)) suffix = fromRegex(regex);
            else suffix = randomFrom(ALNUM, safeLength);
            String value = safePrefix + suffix;
            validateLocalPart(value);
            if (generated.add(value)) return value;
        }
        throw new IllegalStateException("无法生成唯一用户名，请增加位数或减少创建数量");
    }

    private int defaultLength(String strategy, int total) {
        if ("st2".equals(strategy)) return Math.max(1, String.valueOf(Math.max(0,total-1)).length());
        return Math.max(5, digitsForCapacity(Math.max(1,total)));
    }
    private int digitsForCapacity(int count) {
        int n=1; long capacity=36;
        while (capacity < count*20L && n<12) { n++; capacity*=36; }
        return n;
    }
    private String randomFrom(String alphabet, int length) {
        StringBuilder b=new StringBuilder(length);
        for(int i=0;i<length;i++) b.append(alphabet.charAt(random.nextInt(alphabet.length())));
        return b.toString();
    }
    private String leftPad(int value, int width) {
        String s=String.valueOf(value);
        if(s.length()>width) throw new IllegalArgumentException("顺序编号超过设定位数，请增加位数");
        StringBuilder b=new StringBuilder(width); while(b.length()+s.length()<width)b.append('0'); return b.append(s).toString();
    }

    /** Safe supported subset: literals, [a-z0-9], ranges, and exact {n}; e.g. ^[a-z]{6}[0-9]{2}$. */
    String fromRegex(String expression) {
        if (expression == null || expression.trim().isEmpty()) throw new IllegalArgumentException("正则代码不能为空");
        String regex=expression.trim();
        if(regex.startsWith("^")) regex=regex.substring(1);
        if(regex.endsWith("$")) regex=regex.substring(0,regex.length()-1);
        Matcher m=TOKEN.matcher(regex); StringBuilder out=new StringBuilder(); int pos=0;
        while(m.find()) {
            if(m.start()!=pos) throw unsupported(expression);
            String token=m.group(1); int min=m.group(2)==null?1:Integer.parseInt(m.group(2));
            int max=m.group(3)==null?min:Integer.parseInt(m.group(3));
            if(min<0 || max<min || max>48) throw new IllegalArgumentException("正则重复位数必须在0到48之间");
            int repeat=min==max?min:min+random.nextInt(max-min+1);
            String choices=choices(token);
            for(int i=0;i<repeat;i++) out.append(choices.charAt(random.nextInt(choices.length())));
            pos=m.end();
        }
        if(pos!=regex.length() || out.length()==0) throw unsupported(expression);
        if(!Pattern.matches(expression,out.toString())) throw unsupported(expression);
        return out.toString();
    }
    private String choices(String token) {
        if(token.startsWith("[")) {
            String body=token.substring(1,token.length()-1); StringBuilder c=new StringBuilder();
            for(int i=0;i<body.length();i++) {
                char a=body.charAt(i);
                if(i+2<body.length() && body.charAt(i+1)=='-') { char z=body.charAt(i+2); if(a>z)throw unsupported(token); for(char x=a;x<=z;x++)c.append(x); i+=2; }
                else c.append(a);
            }
            if(c.length()==0)throw unsupported(token); return c.toString();
        }
        if(token.startsWith("\\")) {
            if("\\d".equals(token))return "0123456789"; if("\\w".equals(token))return ALNUM; return token.substring(1);
        }
        if(".".equals(token)) return ALNUM;
        if("()|+*?".contains(token)) throw unsupported(token);
        return token;
    }
    private IllegalArgumentException unsupported(String regex) { return new IllegalArgumentException("不支持的正则代码: "+regex+"。支持文字、字符集、范围和 {n}/{n,m}，例如 ^[a-z]{6}[0-9]{2}$"); }
    private void validateLocalPart(String value) {
        if(value.isEmpty())throw new IllegalArgumentException("生成的用户名不能为空");
        if(value.length()>64)throw new IllegalArgumentException("用户名 @ 前部分不能超过64个字符");
        if(!value.matches("[a-z0-9._-]+"))throw new IllegalArgumentException("用户名只支持 a-z、0-9、点、下划线和连字符");
        if(value.startsWith(".")||value.endsWith("."))throw new IllegalArgumentException("用户名不能以点开头或结尾");
    }
}
