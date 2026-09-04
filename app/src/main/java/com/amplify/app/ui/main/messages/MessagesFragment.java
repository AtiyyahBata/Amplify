package com.amplify.app.ui.main.messages;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.amplify.app.adapters.MessagePreviewAdapter;
import com.amplify.app.data.DummyData;
import com.amplify.app.databinding.FragmentMessagesBinding;
import com.amplify.app.ui.messaging.ConversationActivity;
import com.amplify.app.ui.messaging.NewMessageActivity;

/** Messages tab. Tapping a row opens the Conversation screen; the + button starts a new one. */
public class MessagesFragment extends Fragment {

    private FragmentMessagesBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        binding = FragmentMessagesBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MessagePreviewAdapter adapter = new MessagePreviewAdapter(DummyData.getConversations(), preview ->
                startActivity(new Intent(getActivity(), ConversationActivity.class)));

        binding.rvConversations.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.rvConversations.setAdapter(adapter);

        binding.btnNewMessage.setOnClickListener(v ->
                startActivity(new Intent(getActivity(), NewMessageActivity.class)));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
