package com.kubolab.gnss.gnssloggerR;

import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

/**
 * Created by KuboLab on 2018/01/26.
 */

public class CoodinateConv {
    public static double[] enu2xyz(double enu[], double orgxyz[]){
        //[Argin]
        //enu   : [e; n; u] ENU座標[m]
        //orgxyz: [x; y; z] ENU座標系の原点のXYZ座標[m]

        // [Argout]
        // posxyz: [x; y; z] ECEF座標系でのXYZ座標[m]

        // [Description]
        // orgxyzを原点とするENU座標系の座標enuを，ECEF座標系（WGS-84）の座標posxyzに変換します．
        double[] orgllh = xyz2llh(orgxyz,0);
        double phi = orgllh[0];
        double lam = orgllh[1];

        double cosphi = Math.cos(phi);
        double coslam = Math.cos(lam);
        double sinphi = Math.sin(phi);
        double sinlam = Math.sin(lam);
        RealMatrix Ti = MatrixUtils.createRealMatrix(new double[][] {{-sinlam, -sinphi*coslam, cosphi*coslam},{coslam, -sinphi*sinlam, cosphi*sinlam},{0, cosphi, sinphi}});
        RealMatrix matenu = MatrixUtils.createRealMatrix(new double[][]{{enu[0],enu[1],enu[2]}});
        RealMatrix matorgxyz = MatrixUtils.createRealMatrix(new double[][]{{orgxyz[0],orgxyz[1],orgxyz[2]}});

        RealMatrix matposxyz = (Ti.multiply(matenu)).add(matorgxyz);
        double[] posxyz = new double[]{matposxyz.getEntry(1,1),matposxyz.getEntry(1,2),matposxyz.getEntry(1,3)};
        return posxyz;

    }

    public static double[] xyz2llh(double posxyz[], int method){
        //[Argin]
        //posxyz: [x; y; z] XYZ座標[m]
        //method: 変換アルゴリズム指定子(Bowring,Bowring2,Iteration,Heikkinen)

        // [Argout]
        // posllh   : [lat; lon; h] lat: 緯度[rad], lon: 経度[rad]，h: 楕円体高[m]

        // [Description]
        // ECEF座標系（WGS-84座標系）の座標posxyzを測地座標（緯度，経度，楕円体高）に変換します．
        // 変換アルゴリズムをmethodにより指定することができます．methodを省略した場合はBowringが
        // 使用されます．
        double posllh[] = new double[3];
        double x = posxyz[0];
        double y = posxyz[1];
        double z = posxyz[2];
        double a = 6378137.0;
        double f = 1/298.257223563;
        double b = a*(1-f);
        double e2 = f*(2-f);
        double m2 = 1/(1-e2) - 1;
        double p = sqrt(Math.pow(x,2) + Math.pow(y,2));
        switch (method){
            case 0:
                double theta = Math.atan(z*a/(p*b));
                double phi = Math.atan((Math.pow(z+m2*b* sin(theta),3))/(Math.pow((p-e2*a*Math.cos(theta)),3)));
                double N = a/sqrt(Math.pow(1-e2* sin(phi),2));
                double h = p/Math.cos(phi)-N;
                posllh[0] = phi;
                posllh[1] = Math.atan2(y,x);
                posllh[2] = h;
        }
        return posllh;
    }
}
