package cubiq.gui;

import cubiq.models.GuiModel;
import javafx.fxml.FXML;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

public class SolverController implements Observer {
     //TODO: Bilderbezeichnung noch ändern, aktuell 2R, ändern auf R2... usw.!!!!

    private String imagePath;

    @FXML
    private HBox solveIconPane;

    public SolverController() {
        imagePath = "/assets/solveIcons/";
    }

    private GuiModel guiModel;

    private List<String> solution = new ArrayList<>();

    private void solveStringConverter() {
        String solveString = guiModel.getSolveString();
        int idx = 0;
        while (true) {
            int idxNew;
            idxNew = solveString.indexOf(",", idx);
            if (idxNew != -1) {
                solution.add((solveString.substring(idx, idxNew)));
                idx = idxNew + 1;
            } else {
                break;
            }
        }
    }

    private void loadCubeIcons() {
        for(int i = 0; i < solution.size(); i++) {
            solveIconPane.getChildren().add(new SolveIcon(solution.get(i)));
        }
    }

    class SolveIcon extends ImageView {
        public SolveIcon(String solveString) {
            Image image = new Image(getClass().getResourceAsStream(imagePath+solveString+".png"));
            this.setFitWidth(97);
            this.setFitHeight(144);
            this.setImage(image);
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        switch ((String) arg) {
            case "startSolver":
                solveStringConverter();
                loadCubeIcons();
                break;
        }
    }

    public void initModel(GuiModel guiModel) {
        this.guiModel = guiModel;
    }
}
