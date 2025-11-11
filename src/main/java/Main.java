import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main  extends Application{

    @Override
    public void start(Stage stage) throws Exception{
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("Group6.fxml"));
        Scene scene = new Scene(fxmlLoader.load());

        // Optional window customization
        stage.setTitle("Car Distance Tracker");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }
}
