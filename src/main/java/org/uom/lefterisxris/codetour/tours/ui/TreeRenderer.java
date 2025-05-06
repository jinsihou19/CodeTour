package org.uom.lefterisxris.codetour.tours.ui;

import com.intellij.ui.render.LabelBasedRenderer;
import icons.CodeTourIcons;
import org.jetbrains.annotations.NotNull;
import org.uom.lefterisxris.codetour.tours.domain.Step;
import org.uom.lefterisxris.codetour.tours.domain.Tour;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;

/**
 * Handles the rendering of each tree item. It is currently used for defining icons
 *
 * @author Eleftherios Chrysochoidis
 * Date: 10/5/2022
 */
public class TreeRenderer extends LabelBasedRenderer.Tree {

   private String selectedTourId;
   private boolean isDragging = false;
   private Object draggedNode = null;
   private Object dropTarget = null;

   public TreeRenderer(String selectedTourId) {
      this.selectedTourId = selectedTourId;
   }

   public void setDragging(boolean dragging, Object draggedNode) {
      this.isDragging = dragging;
      this.draggedNode = draggedNode;
   }

   public void setDropTarget(Object target) {
      this.dropTarget = target;
   }

   @Override
   public @NotNull Component getTreeCellRendererComponent(@NotNull JTree tree, Object value, boolean sel,
                                                          boolean expanded, boolean leaf,
                                                          int row, boolean hasFocus) {

      final Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
      
      if (value instanceof DefaultMutableTreeNode) {
         final DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
         final Object userObject = node.getUserObject();
         
         // 设置图标
         if (userObject instanceof Tour) {
            final Tour tour = (Tour)userObject;
            if (tour.getId() != null && tour.getId().equals(selectedTourId))
               setIcon(CodeTourIcons.LOGO_XS);
         } else if (userObject instanceof Step) {
            setIcon(CodeTourIcons.STEP);
         }

         // 拖动效果
         if (isDragging) {
            if (userObject == draggedNode) {
               // 被拖动的节点显示半透明
               setForeground(new Color(128, 128, 128, 128));
               setOpaque(true);
            } else if (userObject == dropTarget) {
               // 设置粗体
               Font currentFont = getFont();
               setFont(currentFont.deriveFont(Font.BOLD));
            }
         } else {
            // 非拖动状态下重置样式
            setOpaque(false);
            setBackground(null);
            setForeground(null);
            // 重置字体
            Font currentFont = getFont();
            setFont(currentFont.deriveFont(Font.PLAIN));
         }
      }

      return component;
   }

   public void setSelectedTourId(String selectedTourId) {
      this.selectedTourId = selectedTourId;
   }
}