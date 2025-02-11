package org.uom.lefterisxris.codetour.tours.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.SlowOperations;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.uom.lefterisxris.codetour.tours.domain.OnboardingAssistant;
import org.uom.lefterisxris.codetour.tours.domain.Props;
import org.uom.lefterisxris.codetour.tours.domain.Step;
import org.uom.lefterisxris.codetour.tours.domain.Tour;
import org.uom.lefterisxris.codetour.tours.service.AppSettingsState;
import org.uom.lefterisxris.codetour.tours.service.Navigator;
import org.uom.lefterisxris.codetour.tours.service.StepRendererPane;
import org.uom.lefterisxris.codetour.tours.service.TourValidator;
import org.uom.lefterisxris.codetour.tours.service.Utils;
import org.uom.lefterisxris.codetour.tours.state.StateManager;
import org.uom.lefterisxris.codetour.tours.state.StepSelectionNotifier;
import org.uom.lefterisxris.codetour.tours.state.TourUpdateNotifier;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Code Tour - Tool Window (Tours Navigation and Management).
 * Contains the editable tree representation of all the available Tours and provides
 * easy single-click navigation on Steps and Prev/Next buttons
 *
 * @author Eleftherios Chrysochoidis
 * Date: 11/4/2022
 */
public class ToolPaneWindow {

    private static final String ID = "Tours Navigation";
    private static final Logger LOG = Logger.getInstance(ToolPaneWindow.class);
    private static final String TREE_TITLE = "Code Tours";

    private final JPanel content;
    private final OnePixelSplitter splitter;
    private Tree toursTree;
    private DefaultTreeModel treeModel;

    private final ToolWindow toolWindow;
    private final Project project;

    public ToolPaneWindow(@NotNull Project project, @NotNull ToolWindow toolWindow) {

        this.toolWindow = toolWindow;
        this.project = project;

        content = new JPanel(new BorderLayout());
        splitter = new OnePixelSplitter(true, 0.5f);
        content.add(splitter, BorderLayout.CENTER);

        createToursTree();

        createNavigationButtons();

        registerMessageBusListener();

        updateToursTree();
    }

    public JPanel getContent() {
        return content;
    }

    /**
     * Handle plugin messaging
     */
    public void registerMessageBusListener() {
        project.getMessageBus().connect().subscribe(TourUpdateNotifier.TOPIC, (TourUpdateNotifier) (tour) -> {
            StateManager.getInstance().getState(project).reloadState();
            updateToursTree();
            selectTourLastStep(tour);
        });

        project.getMessageBus().connect().subscribe(StepSelectionNotifier.TOPIC, (StepSelectionNotifier) (step) -> {
            StateManager.getInstance().getActiveTour(project).ifPresent(tour -> {
                if (!toolWindow.isVisible()) {
                    toolWindow.show();
                }
                selectTourStep(tour, StateManager.getInstance().getState(project).getActiveStepIndex());
            });
        });
    }

    private void createToursTree() {

        final String activeId = StateManager.getInstance().getActiveTour(project).map(Tour::getId).orElse("Null");

        treeModel = new DefaultTreeModel(new DefaultMutableTreeNode(TREE_TITLE), false);
        toursTree = new Tree(treeModel);
        toursTree.setCellRenderer(new TreeRenderer(activeId));

        toursTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                final int selectedRow = toursTree.getRowForLocation(e.getX(), e.getY());
                final TreePath pathSelected = toursTree.getPathForLocation(e.getX(), e.getY());

                if (selectedRow < 0 || pathSelected == null) {
                    return;
                }


                if (!(pathSelected.getLastPathComponent() instanceof DefaultMutableTreeNode)) return;
                final DefaultMutableTreeNode node = (DefaultMutableTreeNode) pathSelected.getLastPathComponent();
                if (node.getUserObject() instanceof String && TREE_TITLE.equals(node.getUserObject().toString())) {
                    rootClickListener(e);
                    return;
                }
                if (node.getUserObject() instanceof Tour) {
                    tourClickListener(e, node);
                    return;
                }
                if (node.getUserObject() instanceof Step) {
                    stepClickListener(e, node, project);
                }
            }
        });


        // 注册拖放源
        DnDManager dndManager = DnDManager.getInstance();
        dndManager.registerSource(new DnDSource() {
            @Override
            public boolean canStartDragging(DnDAction action, Point point) {
                TreePath path = toursTree.getPathForLocation(point.x, point.y);
                return path != null; // 允许拖动非空节点
            }

            @Override
            public DnDDragStartBean startDragging(DnDAction action, Point point) {
                TreePath path = toursTree.getPathForLocation(point.x, point.y);
                if (path != null) {
                    return new DnDDragStartBean(path.getLastPathComponent().toString());
                }
                return null;
            }

            @Override
            public void dragDropEnd() {
                System.out.println("拖动结束");
            }

        }, toursTree);

        // 注册拖放目标
        dndManager.registerTarget(new DnDTarget() {
            @Override
            public boolean update(DnDEvent event) {
                // 在拖动过程中，更新拖动目标的外观
                Object attachedObject = event.getAttachedObject();
                if (attachedObject instanceof String) {
                    event.setDropPossible(true, "Drop here!");
                }
                return true;
            }

            @Override
            public void drop(DnDEvent event) {
                Object attachedObject = event.getAttachedObject();
                if (attachedObject instanceof String) {
                    System.out.println("Dropped: " + attachedObject);
                }
            }
        }, toursTree);


        final JPanel treePanel = new JPanel(new BorderLayout());
        treePanel.setName("treePanel");
        final JBScrollPane scrollPane = new JBScrollPane(toursTree);
        treePanel.add(scrollPane, BorderLayout.CENTER);
        for (int i = 0; i < splitter.getComponentCount(); i++) {
            if ("treePanel".equals(splitter.getComponent(i).getName())) {
                splitter.remove(i);
                break;
            }
        }
        splitter.setFirstComponent(treePanel);
    }

    /**
     * 更新指南树数据
     */
    public void updateToursTree() {
        final List<Tour> tours = StateManager.getInstance().getState(project).getTours();
        final DefaultMutableTreeNode root = new DefaultMutableTreeNode(TREE_TITLE);
        tours.forEach(tour -> {
            DefaultMutableTreeNode aTourNode = new DefaultMutableTreeNode(tour);
            LOG.info(String.format("Rendering Tour '%s' with %s steps%n", tour.getTitle(), tour.getSteps().size()));
            tour.getSteps().forEach(step -> aTourNode.add(new DefaultMutableTreeNode(step)));
            root.add(aTourNode);
        });
        treeModel.setRoot(root);
        treeModel.reload();
    }


    private void createOrUpdateContent(@NotNull Step step, @NotNull Project project) {
        StepRendererPane pane = new StepRendererPane(step, project);
        splitter.setSecondComponent(pane);
    }

    private void createNavigationButtons() {
        final JButton previousButton = new JButton("Previous Step");
        previousButton.setToolTipText("Navigate to the Previous Step of the active Tour (Ctrl+Alt+Q)");
        previousButton.addActionListener(e -> {
            LOG.info("Previous button pressed!");

            // Navigate to the previous Step if exist
            StateManager.getInstance().getActiveTour(project).ifPresent(tour ->
                    StateManager.getInstance().getState(project).getPrevStep().ifPresent(step ->
                            selectTourStep(tour, StateManager.getInstance().getActiveStepIndex(project))));

        });

        final JButton nextButton = new JButton("Next Step");
        nextButton.setToolTipText("Navigate to the Next Step of the active Tour (Ctrl+Alt+W)");
        nextButton.addActionListener(e -> {
            LOG.info("Next button pressed!");

            // Navigate to the next Step if exist
            StateManager.getInstance().getActiveTour(project).ifPresent(tour ->
                    StateManager.getInstance().getState(project).getNextStep().ifPresent(step ->
                            selectTourStep(tour, StateManager.getInstance().getState(project).getActiveStepIndex())));
        });

        final JButton reloadButton = new JButton("Reload");
        reloadButton.setToolTipText("Reload the tours from the related files");
        reloadButton.addActionListener(e -> {
            LOG.info("Re-creating the tree");
            reloadToursState();
        });

        final JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(previousButton);
        buttonsPanel.add(nextButton);
        buttonsPanel.add(reloadButton);
        content.add(buttonsPanel, BorderLayout.SOUTH);
    }

    //region Tree Nodes listeners
    private void rootClickListener(MouseEvent e) {
        // Create new Tour option
        if (e.getButton() == MouseEvent.BUTTON3) {
            final JBPopupMenu menu = new JBPopupMenu("Tour Context Menu");

            // Create new Tour
            final JMenuItem newTourAction = new JMenuItem("Create New Tour", AllIcons.Actions.AddFile);
            newTourAction.addActionListener(d -> createNewTourListener());

            // Enable/Disable Virtual Onboarding Assistant
            final String title = String.format("%s Virtual Onboarding Assistant",
                    AppSettingsState.getInstance().isOnboardingAssistantOn() ? "Disable" : "Enable");
            final Icon icon = AppSettingsState.getInstance().isOnboardingAssistantOn()
                    ? AllIcons.Actions.IntentionBulbGrey
                    : AllIcons.Actions.IntentionBulb;
            final JMenuItem toggleOnboardTourAction = new JMenuItem(title, icon);
            toggleOnboardTourAction.addActionListener(d -> {
                AppSettingsState.getInstance().toggleOnboardingAssistant();
                reloadToursState();
            });

            Arrays.asList(newTourAction, toggleOnboardTourAction).forEach(menu::add);
            menu.show(toursTree, e.getX(), e.getY());
        }
    }

    private void tourClickListener(MouseEvent e, DefaultMutableTreeNode node) {
        final Tour tour = (Tour) node.getUserObject();
        // On Tour right click, show a context menu (Delete, Edit)
        if (e.getButton() == MouseEvent.BUTTON3) {
            final JBPopupMenu menu = new JBPopupMenu("Tour Context Menu");

            // Add new Step
            final JMenuItem newStepAction = new JMenuItem("Add new Step", AllIcons.Actions.AddFile);
            newStepAction.addActionListener(d -> addNewStepOnTourListener(tour));

            // Edit Action
            final JMenuItem editAction = new JMenuItem("Edit Tour", AllIcons.Actions.Edit);
            editAction.addActionListener(d -> editTourListener(tour));

            // Jump to Source Action
            final JMenuItem jumpToSourceAction = new JMenuItem("Jump to .tour Source", AllIcons.Actions.EditSource);
            jumpToSourceAction.addActionListener(d -> jumpToSourceTourListener(tour));

            // Delete Action
            final JMenuItem deleteAction = new JMenuItem("Delete Tour", AllIcons.Actions.DeleteTag);
            deleteAction.addActionListener(d -> deleteTourListener(tour));

            if (tour.getTitle().equals(OnboardingAssistant.ONBOARD_ASSISTANT_TITLE)) {
                // Disable Onboarding Assistant Action
                final JMenuItem disableOnboardAssistantAction = new JMenuItem("Disable Onboarding Assistant",
                        AllIcons.Actions.IntentionBulbGrey);
                disableOnboardAssistantAction.addActionListener(d -> {
                    AppSettingsState.getInstance().setOnboardingAssistant(false);
                    reloadToursState();
                });
                menu.add(disableOnboardAssistantAction);

                Arrays.asList(newStepAction, editAction, jumpToSourceAction, deleteAction)
                        .forEach(item -> item.setEnabled(false));
            }

            Arrays.asList(newStepAction, editAction, jumpToSourceAction, deleteAction).forEach(menu::add);
            menu.show(toursTree, e.getX(), e.getY());
        } else {
            if (e.getClickCount() == 2) {
                updateActiveTour(tour);
                StateManager.getInstance().resetActiveStepIndex(project);
                toursTree.repaint();
            }
        }
    }

    private void stepClickListener(MouseEvent e, DefaultMutableTreeNode node, Project project) {
        final Step step = (Step) node.getUserObject();
        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) node.getParent();
        final Tour tour = (Tour) parentNode.getUserObject();
        updateActiveTour(tour);

        if (e.getButton() == MouseEvent.BUTTON3) {
            final JBPopupMenu menu = new JBPopupMenu("Tour Context Menu");

            // Edit Step Action
            final JMenuItem editDescriptionAction = new JMenuItem("Edit Step", AllIcons.Actions.EditScheme);
            editDescriptionAction.addActionListener(d -> editStepListener(step, tour));

            // Move up Step
            final JMenuItem moveUpAction = new JMenuItem("Move Up", AllIcons.Actions.MoveUp);
            moveUpAction.addActionListener(d -> moveListener(step, tour, true));
            moveUpAction.setEnabled(node.getPreviousSibling() != null);

            // Move down Step
            final JMenuItem moveDownAction = new JMenuItem("Move Down", AllIcons.Actions.MoveDown);
            moveDownAction.addActionListener(d -> moveListener(step, tour, false));
            moveDownAction.setEnabled(node.getNextSibling() != null);

            // Delete Action
            final JMenuItem deleteAction = new JMenuItem("Delete Step", AllIcons.Actions.DeleteTag);
            deleteAction.addActionListener(d -> deleteStepListener(step, tour));

            if (tour.getTitle().equals(OnboardingAssistant.ONBOARD_ASSISTANT_TITLE)) {
                Arrays.asList(editDescriptionAction, moveUpAction, moveDownAction, deleteAction)
                        .forEach(item -> item.setEnabled(false));
            }

            Arrays.asList(editDescriptionAction, moveUpAction, moveDownAction, deleteAction)
                    .forEach(menu::add);
            menu.show(toursTree, e.getX(), e.getY());
            return;
        }

        final int index = parentNode.getIndex(node);
        if (index >= 0)
            StateManager.getInstance().getState(project).setActiveStepIndex(index);
        Navigator.navigate(step, project, this::createOrUpdateContent);
    }
    //endregion

    private void createNewTourListener() {
        final Tour newTour = Tour.builder()
                .id(UUID.randomUUID().toString())
                .touFile("newTour" + Props.TOUR_EXTENSION_FULL)
                .title("A New Tour")
                .description("A New Tour")
                .createdAt(LocalDateTime.now())
                .steps(new ArrayList<>())
                .build();

        // Interactive creation (Title and filename) making sure that they are unique
        final Set<String> tourTitles = StateManager.getInstance().getState(project).getTours().stream()
                .map(Tour::getTitle)
                .collect(Collectors.toSet());
        final String updatedTitle = Messages.showInputDialog(project,
                "Input the title of the new Tour (should be unique)",
                "New Tour", AllIcons.Actions.NewFolder, newTour.getTitle(),
                new TourValidator(title -> StringUtils.isNotEmpty(title) && !tourTitles.contains(title)));
        if (updatedTitle == null) return; // i.e. hit cancel
        newTour.setTitle(updatedTitle);


        // Just make sure that the new file is unique
        final Set<String> tourFiles = StateManager.getInstance().getState(project).getTours().stream()
                .map(Tour::getTourFile)
                .collect(Collectors.toSet());
        final String updatedFilename = Messages.showInputDialog(project,
                "Input the file name of the new Tour (should end with .tour and be unique)",
                "New Tour", AllIcons.Actions.NewFolder, Utils.fileNameFromTitle(newTour.getTitle()),
                new TourValidator(fileName -> StringUtils.isNotEmpty(fileName) &&
                        fileName.endsWith(Props.TOUR_EXTENSION_FULL) && !tourFiles.contains(fileName)));
        if (updatedFilename == null) return; // i.e. hit cancel
        newTour.setTourFile(updatedFilename);

        StateManager.getInstance().getState(project).createTour(project, newTour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, newTour, "Creation",
                String.format("Tour '%s' (file %s) has been created", newTour.getTitle(), newTour.getTourFile()));
    }

    //region Tour Context menu actions
    private void addNewStepOnTourListener(Tour tour) {
        MessageDialogBuilder.yesNo("", "");
        final boolean createDescriptionOnlyStep =
                MessageDialogBuilder.yesNo("Step Creation - Create a Description-Only Step?",
                                "To create a new Tour Step with navigation, go to the file you want to add a Step, " +
                                        "<kbd>Right Click on the Editor's Gutter</kbd> (i.e. next to line numbers) > <kbd>Add Tour Step</kbd>" +
                                        "\nHowever, you can create a Description-only Step (without navigation) from this option." +
                                        "\nDo you want to create a Description-Only Step?")
                        .ask(project);

        if (createDescriptionOnlyStep) {
            final Step step = Step.builder()
                    .title("A Description-Only Step")
                    .description("# Simple Description\nI won't navigate you anywhere")
                    .build();

            // Provide a dialog for Step editing
            final StepEditor stepEditor = new StepEditor(project, step);
            final boolean okSelected = stepEditor.showAndGet();
            if (!okSelected) return; // i.e. cancel the step creation

            final Step updatedStep = stepEditor.getUpdatedStep();
            tour.getSteps().add(updatedStep);
            StateManager.getInstance().getState(project).updateTour(tour);

            // Notify UI to re-render
            project.getMessageBus().syncPublisher(TourUpdateNotifier.TOPIC).tourUpdated(tour);
        }
    }

    private void editTourListener(Tour tour) {

        final String updatedTitle = Messages.showInputDialog(project, "Edit Tour's title",
                "Edit Tour", AllIcons.Actions.Edit, tour.getTitle(), null);
        if (updatedTitle == null || updatedTitle.equals(tour.getTitle())) return;

        tour.setTitle(updatedTitle);
        StateManager.getInstance().getState(project).updateTour(tour);

        LOG.info("Active Tour: " + tour.getTitle());
        updateActiveTour(tour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, tour, "Tour Update",
                String.format("Tour's '%s' Title has been updated", tour.getTitle()));

        // Expand and select the first Step of the active Tour on the tree
        selectTourStep(tour, tour.getSteps().isEmpty() ? -1 : 0, false);
    }

    private void jumpToSourceTourListener(Tour tour) {
        SlowOperations.allowSlowOperations(() -> {
            // 先从Tour中找
            VirtualFile tourFile = tour.getVirtualFile();
            if (tourFile != null) {
                new OpenFileDescriptor(project, tourFile, 0).navigate(true);
                return;
            }
            // 再从索引中捞一把
            final Collection<VirtualFile> virtualFiles = FilenameIndex.getVirtualFilesByName(tour.getTourFile(),
                    GlobalSearchScope.projectScope(project));
            final Optional<VirtualFile> virtualFile = virtualFiles.stream()
                    .filter(file -> !file.isDirectory() && file.getName().equals(tour.getTourFile()))
                    .findFirst();

            if (virtualFile.isEmpty()) {
                CodeTourNotifier.error(project, String.format("Could not locate navigation target '%s' for Tour '%s'",
                        tour.getTourFile(), tour.getTitle()));
                return;
            }

            // Navigate
            new OpenFileDescriptor(project, virtualFile.get(), 0).navigate(true);
        });
    }

    private void deleteTourListener(Tour tour) {
        StateManager.getInstance().getState(project).deleteTour(tour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, tour, "Deletion", String.format("Tour " +
                "'%s' (file %s) has been deleted", tour.getTitle(), tour.getTourFile()));
    }
    //endregion

    //region Step Context menu actions
    private void editStepListener(Step step, Tour tour) {
        final int index = tour.getSteps().indexOf(step);

        // Prompt dialog for Step update
        final StepEditor stepEditor = new StepEditor(project, step);
        final boolean okSelected = stepEditor.showAndGet();
        if (!okSelected || !stepEditor.isDirty()) return;

        final Step updatedStep = stepEditor.getUpdatedStep();
        tour.getSteps().set(index, updatedStep);

        StateManager.getInstance().getState(project).updateTour(tour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, tour, "Step Update",
                String.format("Step '%s' has been updated", step.getTitle()));

        // Expand and select the Step on the tree
        selectTourStep(tour, index, false);
    }

    private void moveListener(Step step, Tour tour, boolean up) {
        final int index = tour.getSteps().indexOf(step);
        final int newIndex = up ? index - 1 : index + 1;
        tour.getSteps().remove(index);
        if (tour.getSteps().size() <= newIndex || newIndex < 0)
            CodeTourNotifier.error(project, String.format("Cannot move Step '%s' %s!",
                    step.getTitle(), up ? "up" : "down"));

        tour.getSteps().add(newIndex, step);

        StateManager.getInstance().getState(project).updateTour(tour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, tour, "Steps Order Update", "Steps have been re-arranged!");

        // Expand and select the last Step of the active Tour on the tree
        selectTourStep(tour, newIndex, false);
    }

    private void deleteStepListener(Step step, Tour tour) {
        final int index = tour.getSteps().indexOf(step);
        tour.getSteps().remove(index);
        StateManager.getInstance().getState(project).updateTour(tour);
        updateToursTree();
        CodeTourNotifier.notifyTourAction(project, tour, "Step Deletion", String.format("Step " +
                "'%s' has been removed from Tour '%s'", step.getTitle(), tour.getTitle()));

        // Expand and select a Step on the tree (on the same index)
        selectTourStep(tour, Math.min(tour.getSteps().size() - 1, index), false);
    }
    //endregion

    /**
     * Persist the selected tour and also notify the tree (for proper rendering)
     */
    private void updateActiveTour(Tour tour) {
        StateManager.getInstance().getState(project).setActiveTour(tour);
        if (toursTree != null && toursTree.getCellRenderer() instanceof TreeRenderer renderer) {
            renderer.setSelectedTourId(tour != null ? tour.getId() : "");
        }
    }

    private void selectTourLastStep(Tour tour) {
        selectTourStep(tour, 0);
    }

    private void selectTourStep(Tour tour, int activeStepIndex) {
        selectTourStep(tour, activeStepIndex, true);
    }

    private void selectTourStep(Tour tour, int activeStepIndex, boolean navigate) {
        // Expand and select the given or the last Step of the active Tour on the tree
        for (int i = 0; i < toursTree.getRowCount(); i++) {
            if (!toursTree.getPathForRow(i).getLastPathComponent().toString().equals(tour.getTitle())) continue;

            final Object component = toursTree.getPathForRow(i).getLastPathComponent();
            if (component instanceof DefaultMutableTreeNode) {
                final DefaultMutableTreeNode pNode = (DefaultMutableTreeNode) component;
                if (pNode.getUserObject() instanceof Tour) {
                    toursTree.expandPath(new TreePath(pNode.getPath()));
                    if (activeStepIndex > 0 && activeStepIndex < toursTree.getRowCount()) {
                        // If activeIndex is provided, select it
                        final DefaultMutableTreeNode stepNodeToSelect =
                                (DefaultMutableTreeNode) pNode.getChildAt(activeStepIndex);
                        toursTree.getSelectionModel().setSelectionPath(new TreePath(stepNodeToSelect.getPath()));
                        // Also navigate to that step (if set)
                        if (navigate)
                            Navigator.navigate((Step) stepNodeToSelect.getUserObject(), project, this::createOrUpdateContent);
                    } else {
                        // otherwise, select the last step of the tour Node, and update the selected step index
                        toursTree.getSelectionModel().setSelectionPath(new TreePath(pNode.getLastLeaf().getPath()));
                        StateManager.getInstance().getState(project)
                                .setActiveStepIndex(((Tour) pNode.getUserObject()).getSteps().size() - 1);
                    }
                }
            }
        }
    }

    private void reloadToursState() {
        StateManager.getInstance().getState(project).reloadState();
        updateActiveTour(null); // reset the activeTour
        updateToursTree();
    }
}