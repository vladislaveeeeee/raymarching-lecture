import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class PracticeTask {

    private long window;
    private int program;
    private int vao;
    private int timeUniform;
    private int resolutionUniform;

    private int width = 1000;
    private int height = 800;

    public static void main(String[] args) {
        new PracticeTask().run();
    }

    public void run() {
        initWindow();
        initOpenGL();
        loop();
        cleanup();
    }

    private void initWindow() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit()) throw new IllegalStateException("Failed to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

        window = glfwCreateWindow(width, height, "Final Scene - Raymarched Lava Lamp", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create window");

        glfwSetFramebufferSizeCallback(window, (w, nw, nh) -> {
            width = nw; height = nh;
            glViewport(0, 0, width, height);
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);
    }

    private void initOpenGL() {
        GL.createCapabilities();
        vao = glGenVertexArrays();
        glBindVertexArray(vao);
        program = createShaderProgram(VERTEX_SHADER, FRAGMENT_SHADER);
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(program);

            glUniform1f(glGetUniformLocation(program, "uTime"), (float) glfwGetTime());
            glUniform2f(glGetUniformLocation(program, "uResolution"), (float) width, (float) height);

            glBindVertexArray(vao);
            glDrawArrays(GL_TRIANGLES, 0, 3);

            glfwSwapBuffers(window);
            glfwPollEvents();
        }
    }

    private void cleanup() {
        glDeleteProgram(program);
        glDeleteVertexArrays(vao);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private int createShaderProgram(String v, String f) {
        int vs = compileShader(GL_VERTEX_SHADER, v);
        int fs = compileShader(GL_FRAGMENT_SHADER, f);
        int p = glCreateProgram();
        glAttachShader(p, vs); glAttachShader(p, fs);
        glLinkProgram(p);
        return p;
    }

    private int compileShader(int type, String src) {
        int s = glCreateShader(type);
        glShaderSource(s, src);
        glCompileShader(s);
        return s;
    }

    private static final String VERTEX_SHADER = """
        #version 330 core
        void main() {
            vec2 pos[3] = vec2[](vec2(-1.0, -1.0), vec2(3.0, -1.0), vec2(-1.0, 3.0));
            gl_Position = vec4(pos[gl_VertexID], 0.0, 1.0);
        }
        """;

    private static final String FRAGMENT_SHADER = """
            #version 330 core
            
             out vec4 fragColor;
            
             uniform float uTime;
             uniform vec2 uResolution;
            
             const int MAX_STEPS = 100;
             const float MAX_DIST = 100.0;
             const float EPSILON = 0.001;
            
             // Ваша функція smin (Поліноміальний плавний мінімум)
             float smin(float a, float b, float k) {
                 float h = max(k - abs(a - b), 0.0) / k;
                 return min(a, b) - h * h * h * k * (1.0 / 6.0);
             }
            
             float sdSphere(vec3 p, float r) {
                 return length(p) - r;
             }
            
             float map(vec3 p) {
                 float res = 1e10; // Початкове велике число
            
                 // 1. Створюємо 5 динамічних крапель
                 for(int i = 0; i < 5; i++) {
                     float t = uTime * (0.3 + float(i) * 0.15);
                     // Зміщуємо сфери по вертикалі (Y) синусоїдою
                     // Радіус сфер змінюється для різноманітності
                     vec3 pos = vec3(
                         sin(t * 0.5 + float(i)) * 0.4, // Невелике коливання по X
                         sin(t) * 1.8,                  // Рух по вертикалі
                         0.0
                     );
                     float sphere = sdSphere(p - pos, 0.5 + sin(float(i)) * 0.1);
                    \s
                     // Зливаємо їх плавним мінімумом (smin)
                     res = smin(res, sphere, 0.8);\s
                 }
            
                 // --- НОВА ЧАСТИНА: БАЗОВА СФЕРА ЗНИЗУ ---
                 // 2. Створюємо одну велику, нерухому сферу знизу
                 // Розташовуємо її на Y = -2.5 (нижче центру) з радіусом 1.2
                 vec3 basePos = vec3(0.0, -2.5, 0.0);
                 float baseSphere = sdSphere(p - basePos, 1.2);
            
                 // 3. Зливаємо базу з динамічними краплями
                 // res = smin(res, baseSphere, 1.0); // k=1.0 для густої тягучості
                 // Або просто smin з тими самими параметрами:
                 return smin(res, baseSphere, 0.8);
             }
            
             float raymarch(vec3 ro, vec3 rd) {
                 float t = 0.0;
                 for (int i = 0; i < MAX_STEPS; i++) {
                     vec3 p = ro + rd * t;
                     float d = map(p);
                     if (d < EPSILON) return t; // Влучили!
                     if (t > MAX_DIST) break; // Занадто далеко
                     t += d;
                 }
                 return -1.0; // Не влучили
             }
            
             vec3 getNormal(vec3 p) {
                 vec2 e = vec2(0.001, 0.0);
                 float dx = map(p + vec3(e.x, e.y, e.y)) - map(p - vec3(e.x, e.y, e.y));
                 float dy = map(p + vec3(e.y, e.x, e.y)) - map(p - vec3(e.y, e.x, e.y));
                 float dz = map(p + vec3(e.y, e.y, e.x)) - map(p - vec3(e.y, e.y, e.x));
                 return normalize(vec3(dx, dy, dz));
             }
            
             void main() {
                 vec2 uv = (gl_FragCoord.xy * 2.0 - uResolution.xy) / uResolution.y;
            
                 vec3 ro = vec3(0.0, 0.0, -6.0); // Камера трохи далі
                 vec3 rd = normalize(vec3(uv, 1.2)); // Напрямок променя
            
                 float t = raymarch(ro, rd);
            
                 // Колір фону (глибокий синьо-сірий)
                 vec3 color = vec3(0.1, 0.1, 0.15);\s
            
                 if (t > 0.0) {
                     vec3 p = ro + rd * t;
                     vec3 n = getNormal(p);
            
                     vec3 lightPos = vec3(3.0, 5.0, -4.0);
                     vec3 l = normalize(lightPos - p);
                     vec3 v = normalize(ro - p);
                     vec3 h = normalize(l + v); // Half-vector для Blinn-Phong
            
                     // --- AMBIENT (Фонове) ---
                     vec3 ambient = vec3(0.05);
            
                     // --- DIFFUSE (Дифузне) ---
                     // Колір лави: градієнт від червоного (знизу) до помаранчевого (зверху)
                     // mix(red, orange, vertical_position * scale + offset)
                     vec3 lavaRed = vec3(1.0, 0.1, 0.0);
                     vec3 lavaOrange = vec3(1.0, 0.6, 0.1);
                     vec3 lavaColor = mix(lavaRed, lavaOrange, p.y * 0.25 + 0.5);\s
            
                     float diff = max(dot(n, l), 0.0);
                     vec3 diffuse = lavaColor * diff;
            
                     // --- SPECULAR (Бліки) ---
                     // Висока степінь (pow(..., 128.0)) робить блік різким та глянцевим
                     float spec = pow(max(dot(n, h), 0.0), 128.0);
                     vec3 specular = vec3(1.0) * spec;
            
                     // Фінальний колір: Ambient + Diffuse + Specular
                     color = ambient + diffuse + specular;
            
                     // Гамма-корекція
                     color = pow(color, vec3(0.4545));
                 }
            
                 fragColor = vec4(color, 1.0);
             }
        """;
}