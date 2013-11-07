(ns active.earth
  (import (javax.swing JFrame)
          (gov.nasa.worldwind BasicModel)
          (gov.nasa.worldwind Configuration)
          (gov.nasa.worldwind.avlist AVKey)
          (gov.nasa.worldwind.awt WorldWindowGLCanvas)
          (gov.nasa.worldwind.geom Position)
          (gov.nasa.worldwind.layers RenderableLayer)
          (gov.nasa.worldwind.render PointPlacemark PointPlacemarkAttributes)))

(defn clear [model layer]
  (let [layers (.getLayers model)
        numberOfRenderables (.getNumRenderables layer)]
    (.remove layers layer)
    (if (> numberOfRenderables 10)
      (.removeRenderable layer (.next (.iterator (.getRenderables layer)))))))

(defn redisplay [model layer]
  (.add (.getLayers model) layer))

(defn add-placemark [model layer latitude longitude altitude text]
  (let [placemark (PointPlacemark. (Position/fromDegrees (Double. latitude) (Double. longitude) (Double. altitude)))]
    (.setLabelText placemark text)
    (.addRenderable layer placemark)))

(defn create-model []
  (let [frame (JFrame. "Earth")
        canvas (WorldWindowGLCanvas.)
        layer (RenderableLayer.)
        model (BasicModel.)]
    (redisplay model layer)
    (doto canvas (.setModel model))
    (doto frame
      (.setDefaultCloseOperation JFrame/EXIT_ON_CLOSE)
      (.add canvas)
      (.setSize 800 600)
      (.setVisible true))
    [model layer]))
