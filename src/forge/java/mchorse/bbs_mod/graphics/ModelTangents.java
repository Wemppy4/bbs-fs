package mchorse.bbs_mod.graphics;

/** Tangent-space basis for the original triangle streams, including skinned BOBJ poses. */
public final class ModelTangents
{
    private ModelTangents() {}

    /** Atlas-style midpoint for each triangle's UV bounds (both triangles of a quad agree). */
    public static float[] midUvs(float[] uv)
    {
        if(uv.length%6!=0)throw new IllegalArgumentException("Mid UVs require triangles");
        float[] out=new float[uv.length];
        for(int i=0;i<uv.length;i+=6)
        {
            float u=(Math.min(uv[i],Math.min(uv[i+2],uv[i+4]))+Math.max(uv[i],Math.max(uv[i+2],uv[i+4])))*.5F;
            float v=(Math.min(uv[i+1],Math.min(uv[i+3],uv[i+5]))+Math.max(uv[i+1],Math.max(uv[i+3],uv[i+5])))*.5F;
            for(int j=0;j<3;j++){out[i+j*2]=u;out[i+j*2+1]=v;}
        }
        return out;
    }

    public static float[] calculate(float[] positions, float[] normals, float[] uvs)
    {
        return calculate(new float[positions.length / 3 * 4], positions, normals, uvs);
    }

    public static float[] calculate(float[] out, float[] p, float[] n, float[] uv)
    {
        int count=p.length/3;
        if(n.length!=p.length||uv.length!=count*2||out.length!=count*4||count%3!=0)
            throw new IllegalArgumentException("Tangent data must describe complete triangles");
        for(int i=0;i<count;i+=3)
        {
            int v=i*3,u=i*2;
            float x1=p[v+3]-p[v],y1=p[v+4]-p[v+1],z1=p[v+5]-p[v+2];
            float x2=p[v+6]-p[v],y2=p[v+7]-p[v+1],z2=p[v+8]-p[v+2];
            float u1=uv[u+2]-uv[u],v1=uv[u+3]-uv[u+1],u2=uv[u+4]-uv[u],v2=uv[u+5]-uv[u+1];
            float determinant=u1*v2-u2*v1;
            float inverse=Math.abs(determinant)>1E-12F?1F/determinant:0;
            float tx=(x1*v2-x2*v1)*inverse,ty=(y1*v2-y2*v1)*inverse,tz=(z1*v2-z2*v1)*inverse;
            float bx=(x2*u1-x1*u2)*inverse,by=(y2*u1-y1*u2)*inverse,bz=(z2*u1-z1*u2)*inverse;
            for(int j=0;j<3;j++)
            {
                int o=(i+j)*3,t=(i+j)*4;
                float nx=n[o],ny=n[o+1],nz=n[o+2],normalLength=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(normalLength<1E-12F){nx=0;ny=1;nz=0;normalLength=1;}
                nx/=normalLength;ny/=normalLength;nz/=normalLength;
                float dot=nx*tx+ny*ty+nz*tz,x=tx-nx*dot,y=ty-ny*dot,z=tz-nz*dot;
                float length=(float)Math.sqrt(x*x+y*y+z*z);
                if(length<1E-12F)
                {
                    /* Collapsed UVs still need a finite orthogonal basis. */
                    if(Math.abs(nx)<.9F){x=1-nx*nx;y=-nx*ny;z=-nx*nz;}
                    else{x=-ny*nx;y=1-ny*ny;z=-ny*nz;}
                    length=(float)Math.sqrt(x*x+y*y+z*z);
                }
                x/=length;y/=length;z/=length;
                out[t]=x;out[t+1]=y;out[t+2]=z;
                /* OptiFine SVertexBuilder / packs reconstruct B as cross(T,N)*w. */
                out[t+3]=(y*nz-z*ny)*bx+(z*nx-x*nz)*by+(x*ny-y*nx)*bz<0?-1:1;
            }
        }
        return out;
    }
}
