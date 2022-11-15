package project.view;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import project.logic.Field2D;
import project.logic.Particle;
import project.logic.Plane;
import project.logic.PotentialLine;
import project.logic.Vector2D;
import project.utils.ResizableCanvas;

public class FieldSimulation {
    private Field2D field;
    private Canvas canvas;
    private GraphicsContext ctx;
    private double firstWidth, firstHeight;

    
    final int MAX_STEPS = 1000; //1000; максимальное колво точек в линии
    final int MIN_STEPS = 10; //10 минимальное число точек. попробовать увеличить если "обрубки",
    final double MIN_EPSILON_DISTANCE = 0.1; //0.1; минимальное расстояние на котором ищется след. точка, попробовать поуменьшать
    final double MAX_EPSILON_DISTANCE = 0.5; //0.5; макс расстояние на котором ищется точка, попробовать увеличить
    final double K_CONSTANT = 9;

    private boolean showField;
    private boolean showLines;
    
    private Particle selectedParticle;
    private Color lineColor;
    double k;

    double[][] potential;
    double maxPotential;
    public FieldSimulation(Field2D f, Canvas c){
        showField = true;
        showLines = true;
        field = f;
        canvas = c;
        ctx = c.getGraphicsContext2D();
        firstWidth=c.getWidth();
        firstHeight=c.getHeight();
        potential = new double[(int)f.getHeight()][(int)f.getWidth()];
        lineColor = Color.PURPLE;
    }

    public void setPotentionalLineColor(Color c){
        lineColor = c;
    }

    public Vector2D convertFieldCoordsToScreen(Vector2D mapCoords){
        return Vector2D.fromCoords(
            (mapCoords.getX()+field.getWidth()/2)/field.getWidth()*canvas.getWidth(), 
            (mapCoords.getY()+field.getHeight()/2)/field.getHeight()*canvas.getHeight());
    }

    public Vector2D convertScreenCoordsToField(Vector2D screenCoords){
        return Vector2D.fromCoords(
            screenCoords.getX()/canvas.getWidth()*field.getWidth()-field.getWidth()/2, 
            screenCoords.getY()/canvas.getHeight()*field.getHeight()-field.getHeight()/2
        );
    }

    Vector2D convertGridCoordsToField(int x, int y){
        return Vector2D.fromCoords(
            (x-Math.round(field.getGridWidth()/2))*field.getWidth()/field.getGridWidth(),
            (y-Math.round(field.getGridHeight()/2))*field.getHeight()/field.getGridHeight()
        );
        
    }

    public void drawArrow(Vector2D coords, double length, double angle){
        
        // Draw a Text
        
        ctx.setFill(Color.BLUE);
        double x = coords.getX();
        double y = coords.getY();
    
        length*=k;
        double offs = 15 * Math.PI / 180.0; 
        angle += Math.PI;
        double xs[] = new double[]{
            x + (length/2 * Math.cos(angle+offs)), x-(length/2 * Math.cos(angle)),
                x + (length/2 * Math.cos(angle-offs))
        };
        double ys[] = new double[]{
            y + (length/2 * Math.sin(angle+offs)), y - (length/2 * Math.sin(angle)),
                y + (length/2 * Math.sin(angle-offs))
        };
        ctx.fillPolygon(xs, ys, 3);

        

    }


    final double e0 = 8.854187e-12;
    public Vector2D E(Vector2D pos){
        Vector2D Echarges = Vector2D.nullVector();
        for (Particle p: field.getParticles()){
            
            Vector2D r = pos.sub(p.getPos());
            double rLen = r.len();
           //if(p.getQ()<0) return Vector2D.nullVector();
            Vector2D ur = r.div(rLen);
            Echarges = Echarges.add(ur.mul(p.getQ()/(rLen*rLen)));
        }
        Echarges = Echarges.div(4*Math.PI*e0);

        Vector2D Eplanes = Vector2D.nullVector();
        
        for (Plane p: field.getPlanes()){
            
            //определить слева или справа плоскости D>0 справа

            //D = (х3 - х1) * (у2 - у1) - (у3 - у1) * (х2 - х1)
            double side = p.sideOfPoint(pos);
            Vector2D E_direction = Vector2D.nullVector();
            if(side>0){
            
                E_direction = p.getDirection()
                    .rotate(p.isNegative()? Math.PI/2: Math.PI*1.5);
            }else if (side<0){
                E_direction = p.getDirection().neg()
                    .rotate(p.isNegative()? Math.PI/2: Math.PI*1.5);
            }
            double E_module = p.getDensity()/(2*e0);
           //if(p.getQ()<0) return Vector2D.nullVector();
            Vector2D E = E_direction.mul(E_module);
            Eplanes = Eplanes.add(E);
        }

        return Echarges.add(Eplanes);



    }


    /*public Vector2D E( Vector2D position ) {
        Vector2D electricField = Vector2D.nullVector();
    
        for(Particle chargedParticle: field.getParticles()){
            double distanceSquared = chargedParticle.getPos().distanceSquared( position );
    
          // Avoid bugs stemming from large or infinite fields (#82, #84, #85).
          // Assign the E-field an angle of zero and a magnitude well above the maximum allowed value.
    
          double distancePowerCube = Math.pow( distanceSquared, 1.5 );
    
          // For performance reasons, we don't want to generate more vector allocations
          Vector2D electricFieldContribution = Vector2D.fromCoords(( position.getX() - chargedParticle.getPos().getX() ) * ( chargedParticle.getQ() ) / distancePowerCube, ( position.getY() - chargedParticle.getPos().getY() ) * ( chargedParticle.getQ() ) / distancePowerCube);

          electricField = electricField.add( electricFieldContribution );
        } 
        electricField = electricField.mul( K_CONSTANT ); // prefactor depends on units
        return electricField;
    }*/
    public double potential(Vector2D pos){
        double F=0;
        for(Particle p: field.getParticles()){
            F+=(p.getQ())/pos.sub(p.getPos()).len();
        }

        for(Plane p: field.getPlanes()){
            F+=(p.getDensity()/(2*e0))*p.sideOfPoint(pos)*p.distanceToPoint(pos);
        }
        return F;
    }
    /*public double potential( Vector2D position ) {
        double electricPotential = 0;
    
        for(Particle chargedParticle: field.getParticles()){
            double distance = chargedParticle.getPos().distance( position );
    
            if ( distance > 0 ) {
              electricPotential += ( chargedParticle.getQ() ) / distance;
            }
        };
    
        electricPotential *= K_CONSTANT; // prefactor depends on units
        return electricPotential;
    
    }*/
    Vector2D getNextPositionAlongEquipotentialWithRK4( Vector2D position, double deltaDistance ) {
        Vector2D initialElectricField = E( position ); // {Vector2}
        if(initialElectricField.len()==0) return Vector2D.nullVector();
        //assert && assert( initialElectricField.magnitude !== 0, 'the magnitude of the electric field is zero: initial Electric Field' );
        Vector2D k1Vector = E( position ).normalize().rotate( Math.PI / 2 ); // {Vector2} normalized Vector along electricPotential
        Vector2D k2Vector = E( position.add( k1Vector.div( deltaDistance / 2 ) ) ).normalize().rotate( Math.PI / 2 ); // {Vector2} normalized Vector along electricPotential
        Vector2D k3Vector = E( position.add( k2Vector.div( deltaDistance / 2 ) ) ).normalize().rotate( Math.PI / 2 ); // {Vector2} normalized Vector along electricPotential
        Vector2D k4Vector = E( position.add( k3Vector.div( deltaDistance ) ) ).normalize().rotate( Math.PI / 2 ); // {Vector2} normalized Vector along electricPotential
        Vector2D deltaDisplacement = Vector2D.fromCoords(
            deltaDistance * ( k1Vector.getX() + 2 * k2Vector.getX() + 2 * k3Vector.getX() + k4Vector.getX() ) / 6, 
            deltaDistance * ( k1Vector.getY() + 2 * k2Vector.getY() + 2 * k3Vector.getY() + k4Vector.getY() ) / 6
        );
        return position.add( deltaDisplacement ); // {Vector2} finalPosition
    }
    Vector2D getNextPositionAlongEquipotentialWithElectricPotential( Vector2D position, double electricPotential, double deltaDistance ) {
        /*
         * General Idea: Given the electric field at point position, find an intermediate point that is 90 degrees
         * to the left of the electric field (if deltaDistance is positive) or to the right (if deltaDistance is negative).
         * A further correction is applied since this intermediate point will not have the same electric potential
         * as the targeted electric potential. To find the final point, the electric potential offset between the targeted
         * and the electric potential at the intermediate point is found. By knowing the electric field at the intermediate point
         * the next point should be found (approximately) at a distance epsilon equal to (Delta V)/|E| of the intermediate point.
         */
        Vector2D initialElectricField = E( position ); // {Vector2}
        //assert && assert( initialElectricField.magnitude !== 0, 'the magnitude of the electric field is zero: initial Electric Field' );
        Vector2D electricPotentialNormalizedVector = initialElectricField.normalize().rotate( Math.PI / 2 ); // {Vector2} normalized Vector along electricPotential
        Vector2D midwayPosition = ( electricPotentialNormalizedVector.mul( deltaDistance ) ).add( position ); // {Vector2}
        Vector2D midwayElectricField = E( midwayPosition ); // {Vector2}
        //assert && assert( midwayElectricField.magnitude !== 0, 'the magnitude of the electric field is zero: midway Electric Field ' );
        double midwayElectricPotential = potential( midwayPosition ); //  {number}
        double deltaElectricPotential = midwayElectricPotential - electricPotential; // {number}
        Vector2D deltaPosition = midwayElectricField.mul( deltaElectricPotential / (midwayElectricField.len()* midwayElectricField.len())); // {Vector2}
    
        // if the second order correction is larger than the first, use a more computationally expensive but accurate method.
        if ( deltaPosition.len() > Math.abs( deltaDistance ) ) {
    
          // use a fail safe method
          return getNextPositionAlongEquipotentialWithRK4( position, deltaDistance );
        }
        else {
          return midwayPosition.add( deltaPosition ); // {Vector2} finalPosition
        }
    }
    /*TODO: select point for line, show values */

    public void updateField(){
        for(int y=0; y<field.getGridHeight(); y++){
            for(int x=0; x<field.getGridWidth(); x++){
                Vector2D pos = convertGridCoordsToField(x, y);
    
                Vector2D vec = E(pos);//Vector2D.fromAngle(Math.toRadians(Math.random() * 360), 0.1);
                field.set(x, y, vec);
                //System.out.println(vec.toString());
            }
        }
    }
    public void update(){
        maxPotential=0;
        for(int y=0;y<field.getHeight();y++){
            for(int x=0;x<field.getWidth();x++){
                double p = potential(Vector2D.fromCoords(x-field.getWidth()/2, y-field.getHeight()/2));
                maxPotential=Math.max(p, maxPotential);
                potential[y][x]=p;

            }
        }
        //maxPotential/=(field.getWidth()+field.getHeight());
       
    }
    /*public List<Vector2D> getPotentionalLine(Vector2D point){
        List<Vector2D> line = new LinkedList<>();
        double pot = potential(point);
        final int len = 1000;
        double d = 0.1;
        for(int i=0;i<len;i++){
            //if(i>20 && point.distance(line.get(0))<=1) break;
            if(Math.abs(point.getX())>field.getWidth()/2 || Math.abs(point.getY())>field.getHeight()/2) break;
            line.add(point);
            point = getNextPositionAlongEquipotentialWithElectricPotential(point, pot, d);
        }
        return line;
    }*/

    double getRotationAngle( List<Vector2D> positionArray ) {
        //assert && assert( positionArray.length > 2, 'the positionArray must contain at least three elements' );
        int length = positionArray.size();
        Vector2D newDeltaPosition = positionArray.get( length - 1 ).sub( positionArray.get( length - 2 ) );
        Vector2D oldDeltaPosition = positionArray.get( length - 2 ).sub( positionArray.get( length - 3 ) );
        return newDeltaPosition.angleBetween( oldDeltaPosition ); // a positive number
    }
    double clamp(double a, double min, double max){
        if(a>max) return max;
        else if (a<min) return min;
        else return a;
    }
    double getAdaptativeEpsilonDistance( double epsilonDistance, List<Vector2D> positionArray, boolean isClockwise ) {
        double deflectionAngle = getRotationAngle( positionArray ); // non negative number in radians
        if ( deflectionAngle == 0 ) {
    
          // pedal to metal
          epsilonDistance = MAX_EPSILON_DISTANCE;
        }
        else {
    
          // shorten the epsilon distance in tight turns, longer steps in straighter stretch
          // 360 implies that a perfect circle could be generated by 360 points, i.e. a rotation of 1 degree doesn't change epsilonDistance.
          epsilonDistance *= ( 2 * Math.PI / 360 ) / deflectionAngle;
        }
        // clamp the value of epsilonDistance to be within this range
        epsilonDistance = clamp( Math.abs( epsilonDistance ), MIN_EPSILON_DISTANCE, MAX_EPSILON_DISTANCE );
        epsilonDistance = isClockwise ? epsilonDistance : -epsilonDistance;
        return epsilonDistance;
    }
    public synchronized List<Vector2D> getPotentionalLine( Vector2D position ) {

        if(field.noObjects()) return new LinkedList<>();
    
        /*
         * General Idea of this algorithm
         *
         * The electricPotential line is found using two searches. Starting from an initial point, we find the electric
         * field at this position and define the point to the left of the electric field as the counterclockwise point,
         * whereas the point that is 90 degree right of the electric field is the clockwise point. The points are stored
         * in a counterclockwise and clockwise array. The search of the clockwise and counterclockwise points done
         * concurrently. The search stops if (1) the number of searching steps exceeds a large number and (2) either the
         * clockwise or counterClockwise point is very far away from the origin. A third condition to bailout of the
         * search is that the clockwise and counterClockwise position are very close to one another in which case we have
         * a closed electricPotential line. Note that if the conditions (1) and (2) are fulfilled the electricPotential
         * line is not going to be a closed line but this is so far away from the screenview that the end user will simply
         * see the line going beyond the screen.
         *
         * After the search is done, the function returns an array of points ordered in a counterclockwise direction,
         * i.e. after joining all the points, the directed line would be made of points that have an electric field
         * pointing clockwise (yes  clockwise) to the direction of the line.
         */
        int stepCounter = 0; // {number} integer
    
        double electricPotential = potential(position);
        Vector2D nextClockwisePosition; // {Vector2}
        Vector2D nextCounterClockwisePosition; // {Vector2}
        Vector2D currentClockwisePosition = position.clone(); // {Vector2}
        Vector2D currentCounterClockwisePosition = position.clone(); // {Vector2}
        ArrayList<Vector2D> clockwisePositionArray = new ArrayList<>();
        ArrayList<Vector2D> counterClockwisePositionArray = new ArrayList<>();
        
        // initial epsilon distance for the two heads.
        
        double clockwiseEpsilonDistance = MIN_EPSILON_DISTANCE;
        double counterClockwiseEpsilonDistance = -clockwiseEpsilonDistance;
        boolean isLineClosed = false;
        boolean isEquipotentialLineTerminatingInsideBounds = false;
        while ( ( stepCounter < MAX_STEPS ) && !isLineClosed &&
                ( isEquipotentialLineTerminatingInsideBounds || ( stepCounter < MIN_STEPS ) ) ) {
    
          nextClockwisePosition = getNextPositionAlongEquipotentialWithElectricPotential(
            currentClockwisePosition,
            electricPotential,
            clockwiseEpsilonDistance );
          nextCounterClockwisePosition = getNextPositionAlongEquipotentialWithElectricPotential(
            currentCounterClockwisePosition,
            electricPotential,
            counterClockwiseEpsilonDistance );
    
          clockwisePositionArray.add( nextClockwisePosition );
          counterClockwisePositionArray.add( nextCounterClockwisePosition );
    
          currentClockwisePosition = nextClockwisePosition;
          currentCounterClockwisePosition = nextCounterClockwisePosition;
    
          stepCounter++;
    
          // after three steps, the epsilon distance is adaptative, i.e. large distance when 'easy', small when 'difficult'
          if ( stepCounter > 3 ) {
    
            // adaptative epsilon distance
            clockwiseEpsilonDistance = this.getAdaptativeEpsilonDistance( clockwiseEpsilonDistance, clockwisePositionArray, true );
            counterClockwiseEpsilonDistance = this.getAdaptativeEpsilonDistance( counterClockwiseEpsilonDistance, counterClockwisePositionArray, false );
    
            //assert && assert( clockwiseEpsilonDistance > 0 ); // sanity check
            //assert && assert( counterClockwiseEpsilonDistance < 0 );
    
            // distance between the two searching heads
            double approachDistance = currentClockwisePosition.distance( currentCounterClockwisePosition );
    
            // logic to stop the while loop when the two heads are getting closer
            if ( approachDistance < clockwiseEpsilonDistance + Math.abs( counterClockwiseEpsilonDistance ) ) {
    
              // we want to perform more steps as the two head get closer but we want to avoid the two heads to pass
              // one another. Let's reduce the epsilon distance
              clockwiseEpsilonDistance = approachDistance/3;/// 6; //TODO: ???
              counterClockwiseEpsilonDistance = -clockwiseEpsilonDistance;
              if ( approachDistance < 2 * MIN_EPSILON_DISTANCE ) {
    
                // if the clockwise and counterclockwise points are close to one another, set this.isLineClosed to true to get out of this while loop
                isLineClosed = true;
              }
            }
          } // end of if (stepCounter>3)
    
          //TODO: bounds!
          // is at least one current head inside the bounds?
          isEquipotentialLineTerminatingInsideBounds = field.containsPoint(currentClockwisePosition) || field.containsPoint(currentCounterClockwisePosition);
            //( this.model.enlargedBounds.containsPoint( currentClockwisePosition ) || this.model.enlargedBounds.containsPoint( currentCounterClockwisePosition ) );
    
        } // end of while()
    
        /*if ( !isLineClosed && isEquipotentialLineTerminatingInsideBounds ) { //&isEquipotentialLineTerminatingInsideBounds
            System.out.println("!!!");
          // see https://github.com/phetsims/charges-and-fields/issues/1
          // this is very difficult to come up with such a scenario. so far this
          // was encountered only with a pure quadrupole configuration.
          // let's redo the entire process but starting a tad to the right so we don't get stuck in our search
          Vector2D weeVector = Vector2D.fromCoords(Math.PI, Math.E);//Vector2D.fromCoords( 0.00031415, 0.00027178 ); // (pi, e)
          return getPotentionalLine( position.add( weeVector ) );
        }*/
    
        // let's order all the positions (including the initial point) in an array in a counterclockwise fashion
        
        ArrayList<Vector2D> reversedArray = new ArrayList<>(clockwisePositionArray);
    
        Collections.reverse(reversedArray);
        // let's return the entire array, i.e. the reversed clockwise array, the initial position, and the counterclockwise array
        reversedArray.add(position);
        reversedArray.addAll(counterClockwisePositionArray );
        /*
        PrintWriter writer =  null;
        try {
            writer = new PrintWriter(new File("myFile.txt"));//new BufferedWriter(new FileWriter("myFile.txt", true));
            writer.println("started \n");
            for(Vector2D p: reversedArray) {
                writer.println(p.toString());
            }
            writer.close();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }*/
        return reversedArray;
    }

    double getDistanceFromLine( Vector2D initialPoint, Vector2D midwayPoint, Vector2D finalPoint ) {
        Vector2D midwayDisplacement = midwayPoint.sub( initialPoint );
        Vector2D finalDisplacement = finalPoint.sub( initialPoint );
        return Math.abs( midwayDisplacement.crossScalar( finalDisplacement.normalize() ) );
    }
    public List<Vector2D> getPrunedLine( List<Vector2D> positionArray ) {
        int length = positionArray.size();
        List<Vector2D> prunedPositionArray = new LinkedList<>(); // {Array.<Vector2>}
    
        if ( length == 0 ) {
          return prunedPositionArray;
        }
    
        // push first data point
        prunedPositionArray.add( positionArray.get(0) );
    
        double maxOffset = 0.001; // in model coordinates, the threshold of visual acuity when rendered on the screen
        int lastPushedIndex = 0; // index of the last positionArray element pushed into prunedPosition
    
        for ( int i = 1; i < length - 1; i++ ) {
          Vector2D lastPushedPoint = prunedPositionArray.get( prunedPositionArray.size() - 1 );
    
          for ( int j = lastPushedIndex; j < i + 1; j++ ) {
            double distance = getDistanceFromLine( lastPushedPoint, positionArray.get( j + 1 ), positionArray.get( i + 1 ) );
            if ( distance > maxOffset ) {
              prunedPositionArray.add( positionArray.get(i) );
              lastPushedIndex = i;
              break; // breaks out of the inner for loop
            }
          }
        }
    
        // push last data point
        prunedPositionArray.add( positionArray.get( length - 1 ) );
        return prunedPositionArray;
      }

    void clearScreen(){
        int grey = 50;
        ctx.setFill(Color.rgb(grey,grey,grey));
        ctx.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    void renderAxis(){
        ctx.setStroke(Color.BLACK);
        ctx.strokeLine(canvas.getWidth()/2, 0, canvas.getWidth()/2, canvas.getHeight());
        ctx.strokeLine(0, canvas.getHeight()/2, canvas.getWidth(), canvas.getHeight()/2);
        //double n = 50;
        //double x = field.getWidth()/2;
        //for(int i=0; i<n; i++){
        //        Vector2D textCoords = convertFieldCoordsToScreen(l.getPos());
        //        ctx.setStroke(Color.BLACK);
        //        ctx.strokeText(Double.toString(Math.round(l.getPotential()*1000.0)/1000.0), textCoords.getX(), textCoords.getY());
        //    }
        //}
    }
    void renderLines(){
        if(field.getLines().size()!=0){
            ctx.setLineWidth(1);
            for(PotentialLine l: field.getLines()){
                ctx.setStroke(l.getColor());
                List<Vector2D> line = l.getPoints();
                for(int i=0;i<line.size()-1;i++){

                    Vector2D p1 = convertFieldCoordsToScreen(line.get(i));
                    Vector2D p2 = convertFieldCoordsToScreen(line.get(i+1));
                    ctx.strokeLine(p1.getX(), p1.getY(), p2.getX(), p2.getY());
                    
                }
                //Vector2D textCoords = convertFieldCoordsToScreen(l.getPos());
                //ctx.setStroke(Color.BLACK);
                //ctx.strokeText(Double.toString(Math.round(l.getPotential()*1000.0)/1000.0), textCoords.getX(), textCoords.getY());
            }
        }
    }
    void renderField(){
        for(int y=0; y<field.getGridHeight(); y++){
            for(int x=0; x<field.getGridWidth(); x++){
                Vector2D vec = field.get(x, y);
                Vector2D renderCoords = convertFieldCoordsToScreen(convertGridCoordsToField(x, y));
                drawArrow(renderCoords, 10, vec.getAngle());
                //ctx.setFill(Color.RED);
                //ctx.fillOval(renderCoords.getX()-1.5, renderCoords.getY()-1.5, 3, 3);
            }
        }
    }

    void renderPlanes(){
        for (Plane p: field.getPlanes()){
            double r=10;
            if(!p.isNegative()) ctx.setStroke(Color.RED);
            else ctx.setStroke(Color.BLUE);
            ctx.setLineWidth(5*k);

            Vector2D start = p.getStartPoint()
            .sub(p.getDirection().mul(p.getStartPoint().distance(Vector2D.fromCoords(-field.getWidth()/2, -field.getHeight()/2))));
            Vector2D end = p.getEndPoint()
            .add(p.getDirection().mul(p.getEndPoint().distance(Vector2D.fromCoords(-field.getWidth()/2, -field.getHeight()/2))));
            Vector2D lineStartCoords = convertFieldCoordsToScreen(start);
            Vector2D lineEndCoords = convertFieldCoordsToScreen(end);
            ctx.strokeLine(lineStartCoords.getX(), lineStartCoords.getY(), lineEndCoords.getX(), lineEndCoords.getY());
        }
        ctx.setLineWidth(1);
    }
    void renderParticles(){
        for (Particle p: field.getParticles()){
            double r=10;
            if(p.getQ()>0) ctx.setFill(Color.RED);
            else ctx.setFill(Color.BLUE);

            Vector2D renderCoords = convertFieldCoordsToScreen(p.getPos());
            ctx.fillOval((renderCoords.getX()-r*k), (renderCoords.getY()-r*k), 2*r*k, 2*r*k);
        }
        if(field.getParticles().size()==0) selectedParticle=null;
        if(selectedParticle!=null){
            double r=20;

            Vector2D renderCoords = convertFieldCoordsToScreen(selectedParticle.getPos());
            ctx.setStroke(Color.GREEN);
            ctx.strokeOval((renderCoords.getX()-r*k), (renderCoords.getY()-r*k), 2*r*k, 2*r*k);
            
        }
    }
    void renderPotential(){
        if(maxPotential!=0){
            for(int y=0;y<field.getHeight();y++){
                for(int x=0;x<field.getWidth();x++){
                    double p = potential[y][x];
                    double d = p/maxPotential;
                    if(d>=1)d=1;
                    if(d<0.25)d*=1;
                    //System.out.println(d);
                    Color c = Color.rgb((int)(d*255), 0, 0);
                    ctx.setFill(c);
                    //System.out.print(d);
                    Vector2D renderCoords = convertFieldCoordsToScreen(Vector2D.fromCoords(x-field.getWidth()/2, y-field.getHeight()/2));
                    ctx.fillRect(renderCoords.getX(), renderCoords.getY(), canvas.getWidth()/field.getWidth(),canvas.getHeight()/field.getHeight());
                }
            }
        }
        
    }
    public void render(){
        k = Math.sqrt((canvas.getWidth()/firstWidth)*(canvas.getHeight()/firstHeight));
        Platform.runLater(()->{
                        
            clearScreen();
            //renderPotential();
          
            renderAxis();
            if(showField) renderField();
            if(showLines) renderLines();

            renderPlanes();
            renderParticles();
            
            
        });

        
    }

    public void selectParticle(Particle p){
        selectedParticle=p;
    }
    public Particle getSelectedParticle(){
        return selectedParticle;
    }

    public void setShowField(boolean f){
        showField = f;
    }
    public void setShowLines(boolean f){
        showLines = f;
    }

}
