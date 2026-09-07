package com.example.ui.features.personnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Role
import com.example.domain.model.AcademicClass
import com.example.domain.repository.ClassRepository
import com.example.core.Session
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * TeacherWorkspaceViewModel — T-237 / RBAC-300 (35th session).
 *
 * The teacher's in-Personnel workspace data: the classes homeroom-assigned
 * to the signed-in teacher (STRICT scoping — an unlinked account sees ZERO
 * classes, never the full catalog). Mirrors the desktop TeacherDashboard
 * (T-235) and the RollCallViewModel teacher filter (homeroomTeacherId ==
 * session userId OR homeroomTeacherName == displayName).
 */
@HiltViewModel
class TeacherWorkspaceViewModel @Inject constructor(
    classRepository: ClassRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    val myClasses: StateFlow<List<AcademicClass>> = classRepository.observe()
        .map { all -> filterTeacherClasses(all, sessionManager.current()) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    companion object {
        /**
         * Pure teacher class-scoping filter (unit-tested in
         * TeacherWorkspaceTest — no Hilt/Robolectric needed).
         *
         * Rules:
         *  - non-teacher or null session → empty (the workspace is
         *    teacher-only; nobody else gets a class list here);
         *  - teacher → homeroom-assigned classes only (id match OR
         *    display-name match, the RollCallViewModel convention);
         *  - unlinked teacher → empty, NEVER the full catalog.
         */
        fun filterTeacherClasses(all: List<AcademicClass>, session: Session?): List<AcademicClass> {
            if (session == null || session.role != Role.TEACHER) return emptyList()
            val teacherId = session.userId
            val teacherName = session.displayName
            return all.filter {
                it.homeroomTeacherId == teacherId ||
                    (it.homeroomTeacherName != null &&
                        it.homeroomTeacherName.equals(teacherName, ignoreCase = true))
            }
        }
    }
}
