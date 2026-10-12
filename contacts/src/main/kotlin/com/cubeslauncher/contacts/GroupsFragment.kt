package com.cubeslauncher.contacts

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.cubeslauncher.contacts.databinding.FragmentContactsGroupsBinding

class GroupsFragment : Fragment() {

    private var _binding: FragmentContactsGroupsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactsGroupsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        loadGroups()
    }

    override fun onResume() {
        super.onResume()
        loadGroups()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadGroups() {
        val activity = requireActivity()
        if (activity is ContactsActivity && !activity.hasPermission(Manifest.permission.READ_CONTACTS)) {
            showEmpty()
            return
        }

        val repository = ContactsRepository(requireContext().contentResolver)
        val groups = repository.readGroups()

        if (groups.isEmpty()) {
            showEmpty()
            return
        }

        binding.groupsRecyclerView.adapter = GroupsAdapter(groups) { group ->
            val intent = Intent(requireContext(), GroupDetailActivity::class.java).apply {
                putExtra(GroupDetailActivity.EXTRA_GROUP_ID, group.id)
                putExtra(GroupDetailActivity.EXTRA_GROUP_TITLE, group.title)
            }
            startActivity(intent)
        }
        binding.emptyState.visibility = View.GONE
        binding.groupsRecyclerView.visibility = View.VISIBLE
    }

    private fun showEmpty() {
        binding.emptyState.visibility = View.VISIBLE
        binding.groupsRecyclerView.visibility = View.GONE
    }
}
