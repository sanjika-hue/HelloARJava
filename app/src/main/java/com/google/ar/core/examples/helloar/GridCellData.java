package com.google.ar.core.examples.helloar;


import android.os.Parcel;
import android.os.Parcelable;

    /**
     * Data class to store grid cell information that can be passed between activities
     */
    public class GridCellData implements Parcelable {
        public int cellNumber;
        public int row;
        public int col;
        public boolean visited;

        public GridCellData(int cellNumber, int row, int col, boolean visited) {
            this.cellNumber = cellNumber;
            this.row = row;
            this.col = col;
            this.visited = visited;
        }

        // Parcelable implementation
        protected GridCellData(Parcel in) {
            cellNumber = in.readInt();
            row = in.readInt();
            col = in.readInt();
            visited = in.readByte() != 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(cellNumber);
            dest.writeInt(row);
            dest.writeInt(col);
            dest.writeByte((byte) (visited ? 1 : 0));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<GridCellData> CREATOR = new Creator<GridCellData>() {
            @Override
            public GridCellData createFromParcel(Parcel in) {
                return new GridCellData(in);
            }

            @Override
            public GridCellData[] newArray(int size) {
                return new GridCellData[size];
            }
        };
    }

