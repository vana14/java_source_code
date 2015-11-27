package com.wp.model;

import com.wp.model.composite.PropertySimple;
import com.wp.model.composite.views.IView;
import com.wp.model.objects.Items;
import com.wp.model.sab.States;
import com.wp.utils.Cast;
import com.wp.utils.Is;

import java.util.*;

/**
 * Данный класс представляет из себя модель товара
 *
 * @author Ivan Yevsyukov
 * @e-mail ivan_yevsyukov@mail.ru
 */
public class Product implements IView<Items> {

    // идентификатор товара
    private long id;

    // индентификатор узла, на котором лежит товар
    private long node_id;

    // состояние товара, опубликован, удален, одобрен, забанен
    private States state;
    
    // дата создания товара
    private Date date;
    
    // название товара
    private String title;
    
    // описание товара
    private String description;
    
    // идентификатор раздела, в котором лежит товар
    private long section_id;
    
    // список id изображений для товара
    private List<Long> images;
    
    // фильтры товара
    private Map<String, PropertySimple> filters;
    
    // цена товара
    private Long price;
    
    // система размеров, в которой был сохранен товар
    private String dimension;
    
    // идентификатор группы, в которой лежит товар
    private Long group_id;
    
    // опубликован или нет 
    private boolean published;
    
    // хэш от конфигурации товара
    private String hash;
    
    public Product() {
    }

    public Product(Items item) {
        apply(item);
    }

    // <editor-fold defaultstate="collapsed" desc="Обрабатываем полученные данные">
    /**
     * Данный метод обрабатыает свойства объекта и записывает их в нужную переменную класса
     * 
     * @param obj объект товара
     */
    @Override
    public void apply(Items obj) {
        Map<String, PropertySimple> map = obj.toMapProperties();

        id = obj.getId();
        node_id = obj.getNode().getId();
        state = obj.getState();
        date = obj.getDate_in();
        title = obj.getValue(PropertyName.TITLE, String.class);

        Object priceObj = obj.getValue(PropertyName.PRICE);
        if (priceObj != null) {
            if (priceObj instanceof Long) {
                price = obj.getValue(PropertyName.PRICE, Long.class,(Long)null);
            } else {
                Long tmp = Cast.toLong(obj.getValue(PropertyName.PRICE, String.class, ""));
                price = (tmp == null || tmp <= 0) ? null : tmp;
            }
        }

        description = obj.getValue(PropertyName.DESCRIPTION, String.class);
        hash = obj.getValue(PropertyName.HASH, String.class, (String) null);
        dimension = obj.getValue(PropertyName.DIMENSION, String.class, (String) null);
        group_id = obj.getValue(PropertyName.GROUP_ID, Long.class, (Long) null);

        //У старых товаров нет свойства IS_PUBLISH, но они должны быть опубликованны
        published = obj.getValue(PropertyName.IS_PUBLISH, Long.class, 1l).longValue() == 1l;

        Object section = obj.getValue(PropertyName.SECTION);
        if (section instanceof Items) {
            section_id = ((Items) section).getId();
        } else {
            section_id = (Long) section;
        }

        List imgs = obj.getValues(PropertyName.IMAGES);

        images = new ArrayList<Long>();

        if (!Is.Empty(imgs)) {
            if (imgs.get(0).getClass().equals(Long.class)) {
                images = (List<Long>) imgs;
            } else {
                for (Items img : (List<Items>) imgs) {
                    images.add(img.getId());
                }
            }
        }

        filters = new HashMap<String, PropertySimple>();
        for (Map.Entry<String, PropertySimple> filter : map.entrySet()) {
            if (!filter.getKey().startsWith(FILTERS.FILTER_PREFIX)) {
                continue;
            }
            filters.put(filter.getKey(), filter.getValue());
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Геттеры и сетторы">
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getNode_id() {
        return node_id;
    }

    public void setNode_id(long node_id) {
        this.node_id = node_id;
    }

    public States getState() {
        return state;
    }

    public void setState(States state) {
        this.state = state;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getSection_id() {
        return section_id;
    }

    public void setSection_id(long section_id) {
        this.section_id = section_id;
    }

    public List<Long> getImages() {
        return images;
    }

    public void setImages(List<Long> images) {
        this.images = images;
    }

    public Map<String, PropertySimple> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, PropertySimple> filters) {
        this.filters = filters;
    }

    public Long getPrice() {
        return price;
    }

    public void setPrice(Long price) {
        this.price = price;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension;
    }

    public Long getGroup_id() {
        return group_id;
    }

    public void setGroup_id(Long group_id) {
        this.group_id = group_id;
    }

    public boolean isPublished() {
        return published;
    }

    public void setPublished(boolean published) {
        this.published = published;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
    // </editor-fold>
}
