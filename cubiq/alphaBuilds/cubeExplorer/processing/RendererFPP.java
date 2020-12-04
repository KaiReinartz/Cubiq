package cubiq.alphaBuilds.cubeExplorer.processing;

import cubiq.alphaBuilds.cubeExplorer.io.InteractionHandlerFFP;
import cubiq.alphaBuilds.cubeExplorer.model.Model;
import com.jogamp.newt.Display;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.javafx.NewtCanvasJFX;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.FPSAnimator;

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;

import static com.jogamp.opengl.GL.*;

public class RendererFPP implements Observer {

    private Model model;
    private GLU glu;
    private GLWindow glWindow;
    private InteractionHandlerFFP interactionHandler;
    
    public RendererFPP() {
        createGLWindow();
    }
    
    private void createGLWindow() {
        Display jfxNewtDisplay = NewtFactory.createDisplay(null, false);
        Screen screen = NewtFactory.createScreen(jfxNewtDisplay, 0);
        GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL2));

        // Enable FSAA (full screen antialiasing)
        caps.setSampleBuffers(true);
        caps.setNumSamples(4);

        glWindow = GLWindow.create(screen, caps);
    }
    
    private void startRenderer() {

        // TODO Cubie test----------------------------------------------------------------------------------------------
        CubieFFP[] cubies = new CubieFFP[27];

        int counter = 0;
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    cubies[counter] = new CubieFFP(x-1, y-1, z-1, new int[]{0, 0});
                    counter++;
                }
            }
        }
        System.out.println(Arrays.toString(cubies[8].getPosition()));
        cubies[8].rotateCubie(2, 1);
        System.out.println(Arrays.toString(cubies[8].getPosition()));
        // TODO Cubie test end------------------------------------------------------------------------------------------

        NewtCanvasJFX glCanvas = new NewtCanvasJFX(glWindow);
        glCanvas.setWidth(700);
        glCanvas.setHeight(700);
        model.getRendererPane().getChildren().add(glCanvas);

        FPSAnimator animator = new FPSAnimator(glWindow, 60, true);
        animator.start();

        interactionHandler = new InteractionHandlerFFP();

        glWindow.addMouseListener(interactionHandler);
        glWindow.addGLEventListener(new GLEventListener() {

            public void init(GLAutoDrawable drawable) {
                GL2 gl = glWindow.getGL().getGL2();

                glu = new GLU();

                // Switch on depth test
                gl.glEnable(GL_DEPTH_TEST);
                gl.glDepthFunc(GL_LESS);

                // Switch on back face culling
                gl.glEnable(GL.GL_CULL_FACE);
                gl.glCullFace(GL.GL_BACK);

                gl.glClearColor(0.04f, 0.07f, 0.12f, 1.0f);
            }

            public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
                GL2 gl = drawable.getGL().getGL2();

                if (height == 0) height = 1;
                gl.glViewport(0, 0, width, height);
                interactionHandler.setWindowWidth(width);
                interactionHandler.setWindowHeight(height);
                gl.glMatrixMode(gl.GL_PROJECTION);
                gl.glLoadIdentity();
                float aspectRatio = (float)width / (float)height;
                glu.gluPerspective(30f, aspectRatio, 0.1, 1000f);
                gl.glMatrixMode(gl.GL_MODELVIEW);
            }

            public void display(GLAutoDrawable drawable) {
                GL2 gl = drawable.getGL().getGL2();
                gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

                gl.glLoadIdentity();

                // Defines the position of the camera
                glu.gluLookAt(-9.5f, 6.1f, 9.5f, 0f, 0f, 0f, 0f, 1.0f, 0f);

                interactionHandler.nextFrame();
/*
                gl.glRotatef(controller.getActualAngleX(), 1f, 0f, 0f); // x
                gl.glRotatef(controller.getActualAngleY(), 0f, 1f, 0f); // y
                gl.glRotatef(controller.getActualAngleZ(), 0f, 0f, 1f); // z
*/

                // Offset to center the cube in the scene
                gl.glTranslatef(-1f, -1f, -1f);

                for (int z = 0; z < 3; z++) {
                    for (int y = 0; y < 3; y++) {
                        for (int x = 0; x < 3; x++) {

                            float hCW = 0.5f;  // Half the width of the cubies (half cubie width)
                            float sCO = 0.51f; // The offset from the stickers to the cubie center (sticker center offset)
                            float hSO = 0.45f; // Half the width of the inner sticker (half sticker outer)
                            float hSI = 0.35f; // Half the width of the outer sticker (half sticker inner)

                            gl.glBegin(GL.GL_TRIANGLE_STRIP); // TODO QUAD Strip
                            gl.glColor3f(0f, 0f, 0f);
                            gl.glVertex3f(hCW, -hCW, hCW);
                            gl.glVertex3f(-hCW, -hCW, hCW);
                            gl.glVertex3f(hCW, -hCW, -hCW);
                            gl.glVertex3f(-hCW, -hCW, -hCW);
                            gl.glVertex3f(-hCW, hCW, -hCW);
                            gl.glVertex3f(-hCW, -hCW, hCW);
                            gl.glVertex3f(-hCW, hCW, hCW);
                            gl.glVertex3f(hCW, -hCW, hCW);
                            gl.glVertex3f(hCW, hCW, hCW);
                            gl.glVertex3f(hCW, -hCW, -hCW);
                            gl.glVertex3f(hCW, hCW, -hCW);
                            gl.glVertex3f(-hCW, hCW, -hCW);
                            gl.glVertex3f(hCW, hCW, hCW);
                            gl.glVertex3f(-hCW, hCW, hCW);
                            gl.glEnd();
                            // X
                            if (x == 0) {
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(0f, 0.62f, 0.33f);
                                gl.glVertex3f(-sCO, -hSO, -hSO);
                                gl.glVertex3f(-sCO, -hSO, hSO);
                                gl.glVertex3f(-sCO, hSO, -hSO);
                                gl.glVertex3f(-sCO, hSO, hSO);
                                gl.glEnd();
                            }

                            if (x == 2) {
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(0.24f, 0.51f, 0.96f);
                                gl.glVertex3f(sCO, -hSO, hSO);
                                gl.glVertex3f(sCO, -hSO, -hSO);
                                gl.glVertex3f(sCO, hSO, hSO);
                                gl.glVertex3f(sCO, hSO, -hSO);
                                gl.glEnd();
                            }

                            // Y
                            if (y == 0) {
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(1f, 1f, 1f);
                                gl.glVertex3f(hSO, -sCO, hSO);
                                gl.glVertex3f(-hSO, -sCO, hSO);
                                gl.glVertex3f(hSO, -sCO, -hSO);
                                gl.glVertex3f(-hSO, -sCO, -hSO);
                                gl.glEnd();
                            }

                            if (y == 2) {
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(0.99f, 0.8f, 0.03f);
                                gl.glVertex3f(-hSO, sCO, hSO);
                                gl.glVertex3f(hSO, sCO, hSO);
                                gl.glVertex3f(-hSO, sCO, -hSO);
                                gl.glVertex3f(hSO, sCO, -hSO);
                                gl.glEnd();
                            }

                            // Z
                            if (z == 0) {
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(0.86f, 0.26f, 0.18f);
                                gl.glVertex3f(hSO, -hSO, -sCO);
                                gl.glVertex3f(-hSO, -hSO, -sCO);
                                gl.glVertex3f(hSO, hSO, -sCO);
                                gl.glVertex3f(-hSO, hSO, -sCO);
                                gl.glEnd();
                            }

                            if (z == 2) {
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(-hSI, -hSI, sCO);
                                gl.glVertex3f(hSI, -hSI, sCO);
                                gl.glVertex3f(-hSI, hSI, sCO);
                                gl.glVertex3f(hSI, hSI, sCO);
                                gl.glEnd();

                                // LEFT ADDON
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(-hSO, -hSI, sCO);
                                gl.glVertex3f(-hSI, -hSI, sCO);
                                gl.glVertex3f(-hSO, hSI, sCO);
                                gl.glVertex3f(-hSI, hSI, sCO);
                                gl.glEnd();

                                // RIGHT ADDON
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(hSI, -hSI, sCO);
                                gl.glVertex3f(hSO, -hSI, sCO);
                                gl.glVertex3f(hSI, hSI, sCO);
                                gl.glVertex3f(hSO, hSI, sCO);
                                gl.glEnd();

                                // TOP ADDON
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(-hSI, hSI, sCO);
                                gl.glVertex3f(hSI, hSI, sCO);
                                gl.glVertex3f(-hSI, hSO, sCO);
                                gl.glVertex3f(hSI, hSO, sCO);
                                gl.glEnd();

                                // BOTTOM ADDON
                                gl.glBegin(GL.GL_TRIANGLE_STRIP);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(-hSI, -hSO, sCO);
                                gl.glVertex3f(hSI, -hSO, sCO);
                                gl.glVertex3f(-hSI, -hSI, sCO);
                                gl.glVertex3f(hSI, -hSI, sCO);
                                gl.glEnd();

                                float[] tFP = {0.35f, 0.45f, 0.38f, 0.45f, 0.40f, 0.44f, 0.42f, 0.43f}; // Triangle fan points

                                // TOP-LEFT ADDON
                                gl.glBegin(GL_TRIANGLE_FAN);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(-0.35f, 0.35f, sCO);
                                gl.glVertex3f(-tFP[0], tFP[1], sCO);
                                gl.glVertex3f(-tFP[2], tFP[3], sCO);
                                gl.glVertex3f(-tFP[4], tFP[5], sCO);
                                gl.glVertex3f(-tFP[6], tFP[7], sCO);
                                gl.glVertex3f(-tFP[7], tFP[6], sCO);
                                gl.glVertex3f(-tFP[5], tFP[4], sCO);
                                gl.glVertex3f(-tFP[3], tFP[2], sCO);
                                gl.glVertex3f(-tFP[1], tFP[0], sCO);
                                gl.glEnd();

                                // TOP-RIGHT ADDON
                                gl.glBegin(GL_TRIANGLE_FAN);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(0.35f, 0.35f, sCO);
                                gl.glVertex3f(tFP[1], tFP[0], sCO);
                                gl.glVertex3f(tFP[3], tFP[2], sCO);
                                gl.glVertex3f(tFP[5], tFP[4], sCO);
                                gl.glVertex3f(tFP[7], tFP[6], sCO);
                                gl.glVertex3f(tFP[6], tFP[7], sCO);
                                gl.glVertex3f(tFP[4], tFP[5], sCO);
                                gl.glVertex3f(tFP[2], tFP[3], sCO);
                                gl.glVertex3f(tFP[0], tFP[1], sCO);
                                gl.glEnd();

                                // BOTTOM-LEFT ADDON
                                gl.glBegin(GL_TRIANGLE_FAN);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(-0.35f, -0.35f, sCO);
                                gl.glVertex3f(-tFP[1], -tFP[0], sCO);
                                gl.glVertex3f(-tFP[3], -tFP[2], sCO);
                                gl.glVertex3f(-tFP[5], -tFP[4], sCO);
                                gl.glVertex3f(-tFP[7], -tFP[6], sCO);
                                gl.glVertex3f(-tFP[6], -tFP[7], sCO);
                                gl.glVertex3f(-tFP[4], -tFP[5], sCO);
                                gl.glVertex3f(-tFP[2], -tFP[3], sCO);
                                gl.glVertex3f(-tFP[0], -tFP[1], sCO);
                                gl.glEnd();

                                // BOTTOM-RIGHT ADDON
                                gl.glBegin(GL_TRIANGLE_FAN);
                                gl.glColor3f(1.0f, 0.42f, 0.0f);
                                gl.glVertex3f(0.35f, -0.35f, sCO);
                                gl.glVertex3f(tFP[0], -tFP[1], sCO);
                                gl.glVertex3f(tFP[2], -tFP[3], sCO);
                                gl.glVertex3f(tFP[4], -tFP[5], sCO);
                                gl.glVertex3f(tFP[6], -tFP[7], sCO);
                                gl.glVertex3f(tFP[7], -tFP[6], sCO);
                                gl.glVertex3f(tFP[5], -tFP[4], sCO);
                                gl.glVertex3f(tFP[3], -tFP[2], sCO);
                                gl.glVertex3f(tFP[1], -tFP[0], sCO);
                                gl.glEnd();
                            }

                            gl.glTranslatef(1f, 0f, 0f);
                        }
                        gl.glTranslatef(-3f, 1f, 0f);
                    }
                    gl.glTranslatef(0f, -3f, 1f);
                }
            }

            public void dispose(final GLAutoDrawable drawable) {
            }
        });
    }

    private void drawSticker(GL2 gl, int axis) { // X = 0, Y = 1, Z = 2
        /*
        if (z == 0) {
            gl.glBegin(GL.GL_TRIANGLE_STRIP);
            gl.glColor3f(0.86f, 0.26f, 0.18f);
            gl.glVertex3f(hSO, -hSO, -sCO);
            gl.glVertex3f(-hSO, -hSO, -sCO);
            gl.glVertex3f(hSO, hSO, -sCO);
            gl.glVertex3f(-hSO, hSO, -sCO);
            gl.glEnd();
         */

        
    }

    @Override
    public void update(Observable o, Object arg) {
        switch ((String)arg) {
            case "startRenderer":
                startRenderer();
                break;
        }
    }

    public void initModel(Model model) {
        this.model = model;
    }
}