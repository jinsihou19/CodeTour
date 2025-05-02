package org.uom.lefterisxris.codetour.tours.service;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.uom.lefterisxris.codetour.tours.domain.Step;
import org.uom.lefterisxris.codetour.tours.ui.CodeTourNotifier;

import java.nio.file.Paths;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Navigator class that navigates the user to the location that a step indicates.
 * Also renders the Step's description to the editor (as notification for now)
 *
 * @author Eleftherios Chrysochoidis
 * Date: 16/4/2022
 */
public class Navigator {

    public static void navigateLine(@NotNull Step step, @NotNull Project project, BiConsumer<Step, Project> renderStep) {
        if (project.getBasePath() == null) return;

        SlowOperations.allowSlowOperations(() -> {

            if (step.getFile() == null) {
                renderStep.accept(step, project);
                return;
            }

            // Try finding the appropriate file to navigate to
            final String stepFileName = Paths.get(step.getFile()).getFileName().toString();
            final List<VirtualFile> validVirtualFiles = FilenameIndex
                    .getVirtualFilesByName(stepFileName, GlobalSearchScope.projectScope(project)).stream()
                    .filter(file -> Utils.isFileMatchesStep(file, step))
                    .collect(Collectors.toList());

            if (validVirtualFiles.isEmpty()) {
                // Case for configured but not found file
                CodeTourNotifier.error(project, String.format("Could not locate navigation target '%s' for Step '%s'",
                        step.getFile(), step.getTitle()));
            } else if (validVirtualFiles.size() > 1) {
                // In case there is more than one file that matches with the Step, prompt User to pick the appropriate one
                final String prompt = "More Than One Target File Found! Select the One You Want to Navigate to:";
                JBPopupFactory.getInstance()
                        .createListPopup(new BaseListPopupStep<>(prompt, validVirtualFiles) {
                            @Override
                            public @Nullable PopupStep<?> onChosen(VirtualFile selectedValue, boolean finalChoice) {

                                navigateLine(step, project, selectedValue);

                                // Show a Popup
                                renderStep.accept(step, project);

                                return super.onChosen(selectedValue, finalChoice);
                            }
                        }).showInFocusCenter();

                // Notify user to be more specific
                CodeTourNotifier.warn(project, "Tip: A Step's file path can be more specific either by having a " +
                        "relative path ('file' property) or by setting the 'directory' property on Step's definition");
                return; // Make sure we return here, because PopUp runs on another Thread (no wait for User input)
            } else {
                // Case for exactly one match. Just use it
                navigateLine(step, project, validVirtualFiles.get(0));
            }

            // Show Step's popup and return
            renderStep.accept(step, project);
        });
    }

    private static void navigateLine(@NotNull Step step, @NotNull Project project, VirtualFile targetVirtualFile) {
        final int line = step.getLine() != null ? step.getLine() - 1 : 0;
        new OpenFileDescriptor(project, targetVirtualFile, Math.max(line, 0), 1)
                .navigate(true);
    }

    public static void navigateCode(@NotNull String navigateUrl, @NotNull Project project) {
        String url = navigateUrl;
        if (navigateUrl.startsWith("navigate://")) {
            url = navigateUrl.substring("navigate://".length());
        }
        if (url.contains("#")) {
            navigateMethod(url, project);
        } else {
            navigateLine(url, project);
        }
    }

    /**
     * 导航到指定的类和方法
     */
    private static void navigateToMethod(String className, String methodName, @NotNull Project project) {
        PsiClass psiClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.allScope(project));

        if (psiClass != null) {
            for (PsiMethod method : psiClass.getMethods()) {
                if (method.getName().equals(methodName)) {
                    Navigatable navigatable = (Navigatable) method.getNavigationElement();
                    if (navigatable.canNavigate()) {
                        navigatable.navigate(true);
                    }
                    return;
                }
            }
        }
    }

    /**
     * 导航代码形如 "MyClass#myMethod"
     *
     * @param navigateUrl 导航url
     * @param project     工程
     */
    public static void navigateMethod(@NotNull String navigateUrl, @NotNull Project project) {

        String[] parts = navigateUrl.split("#");
        if (parts.length == 2) {
            String className = parts[0];
            String methodName = parts[1];
            navigateToMethod(className, methodName, project);
        }
    }

    /**
     * 导航代码形如 "MyJava.java:1"
     *
     * @param navigateUrl 导航url
     * @param project     工程
     */
    public static void navigateLine(@NotNull String navigateUrl, @NotNull Project project) {
        String fileName = navigateUrl;
        int line = 0;
        if (navigateUrl.contains(":")) {
            fileName = navigateUrl.substring(0, navigateUrl.indexOf(":"));
            line = Integer.parseInt(navigateUrl.substring(navigateUrl.indexOf(":") + 1));
        }

        final List<VirtualFile> validVirtualFiles = FilenameIndex
                .getVirtualFilesByName(fileName, GlobalSearchScope.projectScope(project)).stream()
                .toList();
        new OpenFileDescriptor(project, validVirtualFiles.get(0), Math.max(line - 1, 0), 1)
                .navigate(true);
    }

}
