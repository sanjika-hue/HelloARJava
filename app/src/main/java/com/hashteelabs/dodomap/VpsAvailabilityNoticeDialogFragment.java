package com.hashteelabs.dodomap;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

/** A DialogFragment for the VPS availability Notice Dialog Box. */
public class VpsAvailabilityNoticeDialogFragment extends DialogFragment {

    /** Listener for a VPS availability notice response. */
    public interface NoticeDialogListener {
        /** Invoked when the user accepts continuing anyway. */
        void onDialogContinueClick(DialogFragment dialog);
    }

    NoticeDialogListener noticeDialogListener;

    static VpsAvailabilityNoticeDialogFragment createDialog() {
        VpsAvailabilityNoticeDialogFragment dialogFragment = new VpsAvailabilityNoticeDialogFragment();
        return dialogFragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        try {
            noticeDialogListener = (NoticeDialogListener) context;
        } catch (ClassCastException e) {
            throw new AssertionError("Must implement NoticeDialogListener", e);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        noticeDialogListener = null;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder
                .setTitle(R.string.vps_unavailable_title)
                .setMessage(R.string.vps_unavailable_message)
                .setCancelable(false)
                .setPositiveButton(
                        R.string.continue_button,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int id) {
                                // Send the positive button event back to the host activity
                                noticeDialogListener.onDialogContinueClick(VpsAvailabilityNoticeDialogFragment.this);
                            }
                        });
        Dialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }
}
