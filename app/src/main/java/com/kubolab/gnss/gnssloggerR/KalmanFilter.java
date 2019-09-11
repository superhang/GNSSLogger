package com.kubolab.gnss.gnssloggerR;

import org.apache.commons.math3.analysis.function.Inverse;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

public class KalmanFilter {
    public static class ReturnValueKal_pre {
        //x_p : nx1 一段予測値
        //P_p ; nxn 一段予測値の推定誤差共分散行列
        public RealMatrix x_p;
        public RealMatrix P_p;
    }
    public static class Kal_pre {
        public ReturnValueKal_pre method(RealMatrix x_f , RealMatrix P_f , RealMatrix F , RealMatrix Q) {
            //x_f : nx1 濾波推定値
            //P_f : nxn 濾波推定値の推定誤差共分散行列
            //F   : nxn 状態推移行列
            //Q   : nxn システム雑音共分散行列
            ReturnValueKal_pre value = new ReturnValueKal_pre();
            //一段予測値の計算
            value.x_p = F.multiply(x_f);
            //推定誤差共分散行列の更新
            RealMatrix transF = F.transpose();
            value.P_p = transF.multiply(F.multiply(P_f)).add(Q);

            return value;
        }
    }

    public static class ReturnValueKal_upd {
        //x_f : nx1 濾波推定値
        //P_f : nxn 濾波推定値の推定誤差共分散行列
        public RealMatrix x_f;
        public RealMatrix P_f;
    }
    public static class Kal_upd {
        public ReturnValueKal_upd method(RealMatrix Z , RealMatrix H , RealMatrix R , RealMatrix x , RealMatrix P) {
            //Z   : mx1 イノベーションベクトル: y-h(x^)
            //H   : mxn 観測行列: 線形化した際の偏微分係数
            //R   : mxm 観測雑音共分散行列
            //x   : nx1 一段予測値
            //P   : nxn 一段予測値の推定誤差共分散行列
            ReturnValueKal_upd value = new ReturnValueKal_upd();
            //カルマンゲインの計算
            RealMatrix transH = H.transpose();
            RealMatrix pre = ((H.multiply(P)).multiply(transH)).add(R);
            RealMatrix preinv = MatrixUtils.blockInverse(pre,0);
            RealMatrix K = (P.multiply(transH)).multiply(preinv);
            //濾波推定値の計算
            value.x_f = x.add(K.multiply(Z));
            //推定誤差共分散行列の計算
            value.P_f = P.subtract((K.multiply(H)).multiply(P));

            return value;
        }
    }
}
